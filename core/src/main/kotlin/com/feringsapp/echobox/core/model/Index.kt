package com.feringsapp.echobox.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IndexMeta(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val count: Int = 0,
    @SerialName("generated_at") val generatedAt: String = "",
)

@Serializable
data class IncrementalManifest(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val files: List<IncrementalFile> = emptyList(),
)

@Serializable
data class IncrementalFile(
    val name: String,
    val from: String = "",
    val to: String = "",
    val count: Int = 0,
)

/** 增量数组的单条：upsert 带 track，delete 带 id。 */
@Serializable
data class IncrementalEntry(
    val op: String,
    val track: Track? = null,
    val id: String? = null,
) {
    companion object {
        const val OP_UPSERT = "upsert"
        const val OP_DELETE = "delete"
    }
}

@Serializable
data class Featured(
    @SerialName("updated_at") val updatedAt: String = "",
    val items: List<FeaturedItem> = emptyList(),
)

@Serializable
data class FeaturedItem(
    @SerialName("track_id") val trackId: String,
    val badge: String = "",
    val blurb: String = "",
)

@Serializable
data class Stats(
    @SerialName("updated_at") val updatedAt: String = "",
    val tracks: Map<String, StatsEntry> = emptyMap(),
)

@Serializable
data class StatsEntry(
    val downloads: Int = 0,
    val plays: Int = 0,
)
