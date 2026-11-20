package com.feringsapp.echobox.media

import com.feringsapp.echobox.core.db.MetaDao
import com.feringsapp.echobox.core.db.SyncStateEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 队列与播放位置的落盘：整段 JSON 存在 sync_state 里，不动表结构。 */
class QueueStore(private val metaDao: MetaDao) {

    @Serializable
    data class PersistedItem(
        val id: String,
        val title: String,
        val artist: String,
        val uri: String,
        val coverUrl: String = "",
        val lyric: String = "",
        val album: String = "",
    )

    @Serializable
    data class PersistedQueue(
        val items: List<PersistedItem>,
        val index: Int = 0,
        val positionMs: Long = 0L,
        val repeatMode: Int = 0,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private companion object {
        const val KEY = "queue_json"
    }

    suspend fun save(items: List<QueueItem>, index: Int, positionMs: Long, repeatMode: Int) {
        val payload = PersistedQueue(
            items = items.map {
                PersistedItem(it.id, it.title, it.artist, it.uri, it.coverUrl, it.lyric, it.album)
            },
            index = index,
            positionMs = positionMs,
            repeatMode = repeatMode,
        )
        metaDao.putState(SyncStateEntity(KEY, json.encodeToString(PersistedQueue.serializer(), payload)))
    }

    suspend fun load(): PersistedQueue? {
        val raw = metaDao.getState(KEY) ?: return null
        return runCatching { json.decodeFromString(PersistedQueue.serializer(), raw) }.getOrNull()
    }

    suspend fun clear() = metaDao.putState(SyncStateEntity(KEY, ""))

    fun toQueueItems(p: PersistedQueue): List<QueueItem> = p.items.map {
        QueueItem(
            id = it.id,
            title = it.title,
            artist = it.artist,
            uri = it.uri,
            coverUrl = it.coverUrl,
            lyric = it.lyric,
            album = it.album,
        )
    }
}
