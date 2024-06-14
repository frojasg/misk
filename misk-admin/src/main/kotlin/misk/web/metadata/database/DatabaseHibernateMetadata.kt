package misk.web.metadata.database

import jakarta.inject.Inject
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

data class DatabaseHibernateMetadata(
  val hibernate: List<DatabaseQueryMetadata>
) : Metadata(metadata = hibernate, formattedJsonString = defaultKotlinMoshi
  .adapter<List<DatabaseQueryMetadata>>()
  .toFormattedJson(hibernate))

class DatabaseHibernateMetadataProvider : MetadataProvider<DatabaseHibernateMetadata> {
  @Inject lateinit var metadata: List<DatabaseQueryMetadata>

  override val id: String = "database-hibernate"

  override fun get() = DatabaseHibernateMetadata(hibernate = metadata)
}
