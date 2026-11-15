package com.feringsapp.echobox.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index("uploadedAtEpoch"),
        Index("statsDownloads"),
        Index("game"),
        Index("seriesName"),
        Index("searchBlob"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val game: String,
    val authorContacts: String,
    val tagsJson: String,
    val seriesName: String?,
    val seriesIndex: Int?,
    val seriesTotal: Int?,
    val authorization: String,
    val resourceLicense: String,
    val fileName: String,
    val format: String,
    val durationSec: Int,
    val coverName: String,
    val sha256: String,
    val uploadedAtEpoch: Long,
    val statsDownloads: Int,
    val statsPlays: Int,
    val searchBlob: String,
    val updatedAtEpoch: Long,
)

@Entity(tableName = "track_tags", primaryKeys = ["trackId", "tag"], indices = [Index("tag")])
data class TrackTagEntity(
    val trackId: String,
    val tag: String,
)

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val name: String,
    val declaredTotal: Int?,
    val trackCount: Int,
)

@Entity(tableName = "http_cache")
data class HttpCacheEntity(
    @PrimaryKey val url: String,
    val etag: String?,
    val lastModified: String?,
    val fetchedAtEpoch: Long,
    val byteSize: Long,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val state: String,
    val filePath: String?,
    val bytesDone: Long,
    val bytesTotal: Long,
    val etag: String?,
    val error: String?,
    val queueOrder: Long,
    val updatedAtEpoch: Long,
)

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val position: Int,
)

@Entity(tableName = "player_state")
data class PlayerStateEntity(
    @PrimaryKey val id: Int = 1,
    val currentTrackId: String?,
    val positionMs: Long,
    val mode: String,
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAtEpoch: Long,
    val positionMs: Long,
)

@Entity(tableName = "mirrors")
data class MirrorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val rawTemplate: String,
    val assetTemplate: String,
    val enabled: Boolean,
    val priority: Int,
    val lastRttMs: Long?,
    val lastCheckEpoch: Long,
)

@Entity(tableName = "submissions")
data class SubmissionEntity(
    @PrimaryKey val recordId: String,
    val state: String,
    val reason: String?,
    val submittedAtEpoch: Long,
    val updatedAtEpoch: Long,
    val payloadJson: String,
    val audioName: String?,
    val coverName: String?,
)
