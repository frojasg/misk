package misk.hibernate

import com.google.inject.Injector
import com.google.inject.Provider
import jakarta.inject.Inject
import misk.ReadyService
import misk.ServiceModule
import misk.concurrent.ExecutorServiceFactory
import misk.healthchecks.HealthCheck
import misk.hibernate.ReflectionQuery.QueryLimitsConfig
import misk.hibernate.testing.TransacterFaultInjector
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.setOfType
import misk.inject.toKey
import misk.inject.typeLiteral
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceDecorator
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.DatabasePool
import misk.jdbc.JdbcModule
import misk.jdbc.RealDatabasePool
import misk.jdbc.SchemaMigratorService
import misk.testing.TestFixture
import misk.web.exceptions.ExceptionMapperModule
import org.hibernate.SessionFactory
import org.hibernate.event.spi.EventType
import org.hibernate.exception.ConstraintViolationException
import java.time.Clock
import javax.persistence.OptimisticLockException
import kotlin.reflect.KClass

/**
 * Binds database connectivity for a qualified data source. This binds the following public types:
 *
 *  * @Qualifier
[misk.jdbc.DataSourceConfig]
 *  * @Qualifier
[SessionFactory]
 *  * @Qualifier
[Transacter]
 *  * [Query.Factory] (with no qualifier)
 *
 * This also registers services to connect to the database ([SessionFactoryService]) and to verify
 * that the schema is up-to-date ([SchemaMigratorService]).
 */

private const val MAX_MAX_ROWS = 10_000
private const val ROW_COUNT_ERROR_LIMIT = 3000
private const val ROW_COUNT_WARNING_LIMIT = 2000

class HibernateModule @JvmOverloads constructor(
  private val qualifier: KClass<out Annotation>,
  config: DataSourceConfig,
  private val readerQualifier: KClass<out Annotation>?,
  readerConfig: DataSourceConfig?,
  val databasePool: DatabasePool = RealDatabasePool,
  private val logLevelConfig: HibernateExceptionLogLevelConfig = HibernateExceptionLogLevelConfig(),
  private val jdbcModuleAlreadySetup: Boolean = false,
  private val installHealthChecks: Boolean = true,
) : KAbstractModule() {
  // Make sure Hibernate logs use slf4j. Otherwise, it will base its decision on the classpath and
  // prefer log4j: https://docs.jboss.org/hibernate/orm/4.3/topical/html/logging/Logging.html
  init {
    System.setProperty("org.jboss.logging.provider", "slf4j")
  }

  val config = config.withDefaults()
  val readerConfig = readerConfig?.withDefaults()

  constructor(
    qualifier: KClass<out Annotation>,
    config: DataSourceConfig,
    databasePool: DatabasePool = RealDatabasePool,
  ) : this(qualifier, config, null, null, databasePool)

  constructor(
    qualifier: KClass<out Annotation>,
    config: DataSourceConfig,
    databasePool: DatabasePool = RealDatabasePool,
    logLevelConfig: HibernateExceptionLogLevelConfig,
  ) : this(qualifier, config, null, null, databasePool, logLevelConfig)

  constructor(
    qualifier: KClass<out Annotation>,
    readerQualifier: KClass<out Annotation>,
    cluster: DataSourceClusterConfig,
    databasePool: DatabasePool = RealDatabasePool,
  ) : this(qualifier, cluster.writer, readerQualifier, cluster.reader, databasePool)

  constructor(
    qualifier: KClass<out Annotation>,
    readerQualifier: KClass<out Annotation>,
    cluster: DataSourceClusterConfig,
    databasePool: DatabasePool = RealDatabasePool,
    installHealthChecks: Boolean = true,
  ) : this(
    qualifier = qualifier,
    config = cluster.writer,
    readerQualifier = readerQualifier,
    readerConfig = cluster.reader,
    databasePool = databasePool,
    installHealthChecks = installHealthChecks
  )

  constructor(
    qualifier: KClass<out Annotation>,
    config: DataSourceConfig,
    databasePool: DatabasePool = RealDatabasePool,
    jdbcModuleAlreadySetup: Boolean,
  ) : this(
    qualifier = qualifier,
    config = config,
    readerQualifier = null,
    readerConfig = null,
    databasePool = databasePool,
    jdbcModuleAlreadySetup = jdbcModuleAlreadySetup
  )

  override fun configure() {
    if (readerQualifier != null) {
      check(readerConfig != null) {
        "Reader not configured for datasource $readerQualifier"
      }
    }

    if (!jdbcModuleAlreadySetup) {
      install(
        JdbcModule(
          qualifier = qualifier,
          config = config,
          readerQualifier = readerQualifier,
          readerConfig = readerConfig,
          databasePool = databasePool,
          installHealthCheck = installHealthChecks
        )
      )
    }

    bind<Query.Factory>().to<ReflectionQuery.Factory>()
    bind<QueryLimitsConfig>()
      .toInstance(QueryLimitsConfig(MAX_MAX_ROWS, ROW_COUNT_ERROR_LIMIT, ROW_COUNT_WARNING_LIMIT))
    bind<HibernateExceptionLogLevelConfig>().toInstance(logLevelConfig)

    bindDataSource(qualifier, true)
    if (readerQualifier != null) {
      bindDataSource(readerQualifier, false)
    }

    newMultibinder<DataSourceDecorator>(qualifier)

    val transacterKey = Transacter::class.toKey(qualifier)
    val sessionFactoryServiceProvider = getProvider(keyOf<SessionFactoryService>(qualifier))
    val readerSessionFactoryServiceProvider = if (readerQualifier != null) {
      getProvider(keyOf<SessionFactoryService>(readerQualifier))
    } else {
      null
    }

    install(
      ServiceModule<SchemaMigratorService>(qualifier)
        .dependsOn<DataSourceService>(qualifier)
        .enhancedBy<ReadyService>()
    )

    bind(transacterKey).toProvider(object : Provider<Transacter> {
      @Inject lateinit var executorServiceFactory: ExecutorServiceFactory
      @Inject lateinit var injector: Injector
      override fun get(): RealTransacter {
        return RealTransacter(
          qualifier = qualifier,
          sessionFactoryService = sessionFactoryServiceProvider.get(),
          readerSessionFactoryService = readerSessionFactoryServiceProvider?.get(),
          config = config,
          executorServiceFactory = executorServiceFactory,
          hibernateEntities = injector.findBindingsByType(HibernateEntity::class.typeLiteral())
            .map {
              it.provider.get()
            }.toSet()
        )
      }
    }).asSingleton()

    /**
     * Reader transacter is only supported for MySQL for now. TiDB and Vitess replica read works
     * a bit differently than MySQL.
     */
    if (readerQualifier != null && config.type == DataSourceType.MYSQL) {
      val readerTransacterKey = Transacter::class.toKey(readerQualifier)
      bind(readerTransacterKey).toProvider(object : Provider<Transacter> {
        @Inject lateinit var executorServiceFactory: ExecutorServiceFactory
        @Inject lateinit var injector: Injector
        override fun get(): Transacter {
          val sessionFactoryService = readerSessionFactoryServiceProvider!!.get()
          val realTransacter = RealTransacter(
            qualifier = readerQualifier,
            sessionFactoryService = sessionFactoryService,
            readerSessionFactoryService = sessionFactoryService,
            config = config,
            executorServiceFactory = executorServiceFactory,
            hibernateEntities = injector.findBindingsByType(HibernateEntity::class.typeLiteral())
              .map {
                it.provider.get()
              }.toSet()
          ).readOnly()
          return realTransacter
        }
      }).asSingleton()
    }

    // Install other modules.
    install(object : HibernateEntityModule(qualifier) {
      override fun configureHibernate() {
        bindListener(EventType.PRE_INSERT).to<TimestampListener>()
        bindListener(EventType.PRE_UPDATE).to<TimestampListener>()
      }
    })

    install(
      ExceptionMapperModule
        .create<RetryTransactionException, RetryTransactionExceptionMapper>()
    )
    install(
      ExceptionMapperModule
        .create<ConstraintViolationException, ConstraintViolationExceptionMapper>()
    )
    install(
      ExceptionMapperModule
        .create<OptimisticLockException, OptimisticLockExceptionMapper>()
    )
  }

  private fun bindDataSource(
    qualifier: KClass<out Annotation>,
    isWriter: Boolean,
  ) {
    // These items are configured on the writer qualifier only
    val entitiesProvider = getProvider(setOfType(HibernateEntity::class).toKey(this.qualifier))
    val eventListenersProvider =
      getProvider(setOfType(ListenerRegistration::class).toKey(this.qualifier))

    val sessionFactoryServiceProvider = getProvider(keyOf<SessionFactoryService>(qualifier))

    // Bind SessionFactoryService as implementation of TransacterService.
    val hibernateInjectorAccessProvider = getProvider(HibernateInjectorAccess::class.java)
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(qualifier))

    bind(keyOf<TransacterService>(qualifier)).to(keyOf<SessionFactoryService>(qualifier))
    bind(keyOf<SessionFactory>(qualifier)).toProvider(keyOf<SessionFactoryService>(qualifier))
    bind(keyOf<SessionFactoryService>(qualifier)).toProvider {
      SessionFactoryService(
        qualifier = qualifier,
        dataSourceService = dataSourceServiceProvider.get(),
        hibernateInjectorAccess = hibernateInjectorAccessProvider.get(),
        entityClasses = entitiesProvider.get(),
        listenerRegistrations = eventListenersProvider.get()
      )
    }.asSingleton()

    if (isWriter) {
      install(
        ServiceModule<TransacterService>(qualifier)
          .enhancedBy<SchemaMigratorService>(qualifier)
          .enhancedBy<ReadyService>()
          .dependsOn<DataSourceService>(qualifier)
      )
    } else {
      install(
        ServiceModule<TransacterService>(qualifier)
          .dependsOn<DataSourceService>(qualifier)
          .enhancedBy<ReadyService>()
      )
    }

    if (this.installHealthChecks) {
      val healthCheckKey = keyOf<HealthCheck>(qualifier)
      bind(healthCheckKey)
        .toProvider(object : Provider<HibernateHealthCheck> {
          @Inject lateinit var clock: Clock

          override fun get() = HibernateHealthCheck(qualifier, sessionFactoryServiceProvider, clock)
        })
        .asSingleton()
      multibind<HealthCheck>().to(healthCheckKey)
    }
  }
}
