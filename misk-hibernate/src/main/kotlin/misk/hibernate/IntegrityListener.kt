package misk.hibernate

import misk.logging.getLogger
import mu.KotlinLogging
import org.hibernate.event.spi.PostLoadEvent
import org.hibernate.event.spi.PostLoadEventListener
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.event.spi.PreUpdateEvent
import org.hibernate.event.spi.PreUpdateEventListener
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encryption listener implements read/write hibernate operations, intercepts them and
 * checks/creates integrity hashes over annotated columns.
 */
@Singleton
internal class IntegrityListener: PreInsertEventListener, PreUpdateEventListener,
    PostLoadEventListener {

  private val logger = getLogger<IntegrityListener>()

  override fun onPreInsert(event: PreInsertEvent): Boolean {
    val entity = event.entity
    if (entity is DbEncryptedEntity) {
      // Find the annotated columns, generate JSON blob over them
      // Hash this blob
      logger.info("WTF")
      return true
    }
    logger.info("WTF")
    return false
  }

  override fun onPreUpdate(event: PreUpdateEvent): Boolean {
    return false
  }

  override fun onPostLoad(event: PostLoadEvent) {
  }
}
