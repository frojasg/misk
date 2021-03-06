package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
internal class SchemaValidatorService internal constructor(
  qualifier: KClass<out Annotation>,
  private val sessionFactoryServiceProvider: Provider<SessionFactoryService>,
  private val config: DataSourceConfig
) : AbstractIdleService(), DependentService {

  override val consumedKeys = setOf<Key<*>>(
      SchemaMigratorService::class.toKey(qualifier),
      SessionFactoryService::class.toKey(qualifier)
  )
  override val producedKeys = setOf<Key<*>>(SchemaValidatorService::class.toKey(qualifier))

  override fun startUp() {
    val validator = SchemaValidator()
    val sessionFactoryService = sessionFactoryServiceProvider.get()
    validator.validate(sessionFactoryService.get(), sessionFactoryService.hibernateMetadata, config)
  }

  override fun shutDown() {
  }
}
