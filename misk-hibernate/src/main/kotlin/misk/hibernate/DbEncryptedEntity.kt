package misk.hibernate

import okio.ByteString

interface DbEncryptedEntity {
  // An encrypted entity has signed columns in it. These columns are verified on load against
  // the encrypted HASH here. This should be done at load time.
  // The idea is that the hash is signed and can't be tampered with, without subsequent detection

  // Ideas, we could have a JSON blob, which has
  // {verifiedData:
  //     [columnName: "<signed hash of column data>",
  //      anotherColumn: "<another hash>", ...]
  // }
  // Pro, this extends easily. Could have all the columns signed, or add an annotation.
  // When the table changes, the encrypted data will be written/read as it appears.
  // There should never be a case where we have unencrypted data.
  // Cons, ... Any?

  // Could also do this with a proto. Define all the columns we want in a proto, and have a
  // handler routine which will need to be implemented per table. When the table changes, the
  // proto must be changed to include new data. This is nice in that it's explicit, but more
  // overhead.

  // Another idea is to just add encryption in the same way as we have done for franklin.
  // Again. This needs to be done via an encryption layer.
  val encrypted_data: ByteString
}