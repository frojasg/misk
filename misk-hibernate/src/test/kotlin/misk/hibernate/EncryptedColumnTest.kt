package misk.hibernate

import misk.MiskServiceModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString
import org.junit.jupiter.api.Test
import javax.inject.Inject
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

@MiskTest(startService = true)
internal class EncryptedColumnTest {
  @MiskTestModule
  val module = TestModule()

  @Inject @EncryptedColumn lateinit var transacter: Transacter

  @Test
  fun basic() {
    transacter.transaction { session -> {
      session.save(DbHeroIdentity())
    }}
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskServiceModule())
      install(EnvironmentModule(Environment.TESTING))

      val config = MiskConfig.load<EncryptedColumnTest.RootConfig>("encryptedcolumn",
          Environment.TESTING)
      install(HibernateTestingModule(EncryptedColumn::class))
      install(HibernateModule(EncryptedColumn::class, config.data_source))
      install(object : HibernateEntityModule(EncryptedColumn::class) {
        override fun configureHibernate() {
          addEntities(DbHeroIdentity::class)
        }
      })
    }
  }

  data class RootConfig(val data_source: DataSourceConfig) : Config

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class EncryptedColumn

  @Entity
  @Table(name = "hero_identity")
  class DbHeroIdentity : DbUnsharded<DbHeroIdentity>, DbEncryptedEntity {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbHeroIdentity>

    @Column(nullable = false)
    lateinit var hero: String

    @Column(nullable = true)
    override lateinit var encrypted_data: ByteString
  }
}
