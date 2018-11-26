package misk.hibernate

import org.hibernate.event.spi.PostLoadEvent
import org.hibernate.event.spi.PostLoadEventListener
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.event.spi.PreUpdateEvent
import org.hibernate.event.spi.PreUpdateEventListener

/**
 * Encryption listener implements read/write hibernate operations, intercepts them and
 * checks/creates integrity hashes over annotated columns.
 */
internal class IntegrityListener: PreInsertEventListener, PreUpdateEventListener,
    PostLoadEventListener {
  override fun onPreInsert(event: PreInsertEvent?): Boolean {
    if (event?.entity is DbEncryptedEntity) {
      // Find the annotated columns, generate JSON blob over them
      // Hash this blob
    }
    return false
  }

  override fun onPreUpdate(event: PreUpdateEvent?): Boolean {
    return false
  }

  override fun onPostLoad(event: PostLoadEvent?) {
  }
}
