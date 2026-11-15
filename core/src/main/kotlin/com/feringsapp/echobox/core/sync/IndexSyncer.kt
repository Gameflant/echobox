package com.feringsapp.echobox.core.sync

import androidx.room.withTransaction
import com.feringsapp.echobox.core.db.EchoDatabase
import com.feringsapp.echobox.core.db.HttpCacheEntity
import com.feringsapp.echobox.core.db.SyncStateEntity
import com.feringsapp.echobox.core.db.TrackTagEntity
import com.feringsapp.echobox.core.model.IncrementalEntry
import com.feringsapp.echobox.core.model.Mirror
import com.feringsapp.echobox.core.network.MirrorResolver
import com.feringsapp.echobox.core.network.SafeHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

sealed interface SyncResult {
    data class Updated(val fetched: Int, val mode: String) : SyncResult
    data object NotModified : SyncResult
    data class Failed(val reason: String) : SyncResult
}

/**
 * 索引同步：
 * 1. meta.json（ETag）判断是否需要拉全量
 * 2. 增量清单 → 逐条 upsert/delete
 * 3. stats.json 合并下载/播放量
 * 4. featured.json 原样存到 sync_state，供首页读取
 */
class IndexSyncer(
    private val db: EchoDatabase,
    private val http: SafeHttp,
    private val resolver: MirrorResolver,
    private val parser: IndexParser = IndexParser(),
) {
    private val metaDao get() = db.metaDao()

    suspend fun sync(forceFull: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        resolver.ensureDefaults()
        // 单选模式：只用用户选中的源，失败直接报错，不静默切别的源
        val mirror = resolver.selected()
        try {
            val result = syncWith(mirror, forceFull)
            if (result is SyncResult.Updated || result is SyncResult.NotModified) {
                syncStats(mirror)
                syncFeatured(mirror)
            }
            result
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: e::class.simpleName ?: "同步失败")
        }
    }

    private suspend fun syncWith(mirror: Mirror, forceFull: Boolean): SyncResult {
        val metaUrl = resolver.rawUrl(mirror, "index/meta.json")
        val cachedMeta = metaDao.cache(metaUrl)
        var needFull = forceFull
        var remoteMeta = cachedMeta

        http.read(metaUrl, etag = cachedMeta?.etag, lastModified = cachedMeta?.lastModified) { handle ->
            if (handle.notModified || handle.stream == null) return@read
            val meta = parser.meta(handle.stream)
            val lastSync = metaDao.getState(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L
            val tooOld = System.currentTimeMillis() - lastSync > FULL_RESYNC_WINDOW_MS
            val countChanged = metaDao.getState(KEY_META_COUNT) != meta.count.toString()
            if (lastSync == 0L || tooOld || countChanged) needFull = true
            remoteMeta = HttpCacheEntity(
                url = metaUrl,
                etag = handle.etag,
                lastModified = handle.lastModified,
                fetchedAtEpoch = System.currentTimeMillis(),
                byteSize = 0,
            )
            metaDao.putState(SyncStateEntity(KEY_META_COUNT, meta.count.toString()))
        }
        remoteMeta?.let { metaDao.putCache(it) }

        if (needFull) return fullSync(mirror)
        return incrementalSync(mirror)
    }

    private suspend fun fullSync(mirror: Mirror): SyncResult {
        val url = resolver.rawUrl(mirror, "index/tracks.json")
        val cached = metaDao.cache(url)
        var fetched = 0

        http.read(url, etag = cached?.etag, lastModified = cached?.lastModified) { handle ->
            if (handle.notModified || handle.stream == null) return@read
            val batch = ArrayList<com.feringsapp.echobox.core.db.TrackEntity>(BATCH)
            val tags = ArrayList<TrackTagEntity>(BATCH * 2)
            db.withTransaction { db.trackDao().deleteAll() }
            for (track in parser.tracks(handle.stream)) {
                val entity = track.toEntity()
                batch += entity
                tags += entity.toTagEntities()
                if (batch.size >= BATCH) {
                    flush(batch, tags)
                    fetched += batch.size
                    batch.clear()
                    tags.clear()
                }
            }
            if (batch.isNotEmpty()) {
                flush(batch, tags)
                fetched += batch.size
            }
            metaDao.putCache(
                HttpCacheEntity(
                    url = url,
                    etag = handle.etag,
                    lastModified = handle.lastModified,
                    fetchedAtEpoch = System.currentTimeMillis(),
                    byteSize = 0,
                ),
            )
            metaDao.putState(SyncStateEntity(KEY_LAST_SYNC, System.currentTimeMillis().toString()))
        }
        return SyncResult.Updated(fetched = fetched, mode = "full")
    }

    private suspend fun incrementalSync(mirror: Mirror): SyncResult {
        val manifestUrl = resolver.rawUrl(mirror, "index/incremental/manifest.json")
        val lastSync = metaDao.getState(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L
        var manifest: com.feringsapp.echobox.core.model.IncrementalManifest? = null
        http.read(manifestUrl, etag = metaDao.cache(manifestUrl)?.etag) { handle ->
            if (handle.stream != null) manifest = parser.manifest(handle.stream)
        }
        val pending = manifest?.files.orEmpty().filter { file ->
            val to = runCatching { Instant.parse(file.to).toEpochMilli() }.getOrDefault(0L)
            to <= 0L || to > lastSync
        }
        if (pending.isEmpty()) {
            metaDao.putState(SyncStateEntity(KEY_LAST_SYNC, System.currentTimeMillis().toString()))
            return SyncResult.NotModified
        }

        var fetched = 0
        for (file in pending) {
            val url = resolver.rawUrl(mirror, "index/incremental/${file.name}")
            http.read(url) { handle ->
                if (handle.stream == null) return@read
                val upserts = ArrayList<com.feringsapp.echobox.core.db.TrackEntity>(BATCH)
                val tags = ArrayList<TrackTagEntity>(BATCH * 2)
                val deletes = ArrayList<String>()
                for (entry in parser.incremental(handle.stream)) {
                    when (entry.op) {
                        IncrementalEntry.OP_DELETE -> entry.id?.let { deletes += it }
                        else -> entry.track?.let {
                            val entity = it.toEntity()
                            upserts += entity
                            tags += entity.toTagEntities()
                            if (deletes.isNotEmpty()) {
                                db.withTransaction { db.trackDao().deleteByIds(deletes.toList()) }
                                deletes.clear()
                            }
                            if (upserts.size >= BATCH) {
                                flush(upserts, tags)
                                fetched += upserts.size
                                upserts.clear()
                                tags.clear()
                            }
                        }
                    }
                }
                if (upserts.isNotEmpty()) {
                    flush(upserts, tags)
                    fetched += upserts.size
                }
                if (deletes.isNotEmpty()) {
                    db.withTransaction { db.trackDao().deleteByIds(deletes.toList()) }
                }
            }
        }
        metaDao.putState(SyncStateEntity(KEY_LAST_SYNC, System.currentTimeMillis().toString()))
        return SyncResult.Updated(fetched = fetched, mode = "incremental")
    }

    private suspend fun flush(
        tracks: List<com.feringsapp.echobox.core.db.TrackEntity>,
        tags: List<TrackTagEntity>,
    ) {
        db.withTransaction {
            db.trackDao().upsertAll(tracks)
            db.tagDao().deleteByTrackIds(tracks.map { it.id })
            db.tagDao().insertAll(tags.distinct())
        }
    }

    private suspend fun syncStats(mirror: Mirror) {
        val url = resolver.rawUrl(mirror, "stats.json")
        runCatching {
            http.read(url, etag = metaDao.cache(url)?.etag) { handle ->
                if (handle.stream == null) return@read
                val stats = parser.stats(handle.stream)
                db.withTransaction {
                    stats.tracks.forEach { (id, entry) ->
                        db.trackDao().updateStats(id, entry.downloads, entry.plays)
                    }
                }
                metaDao.putCache(
                    HttpCacheEntity(url, handle.etag, handle.lastModified, System.currentTimeMillis(), 0),
                )
            }
        }
    }

    private suspend fun syncFeatured(mirror: Mirror) {
        val url = resolver.rawUrl(mirror, "featured.json")
        runCatching {
            http.read(url, etag = metaDao.cache(url)?.etag) { handle ->
                if (handle.stream == null) return@read
                val bytes = handle.stream.readBytes()
                metaDao.putState(SyncStateEntity(KEY_FEATURED, bytes.decodeToString()))
                metaDao.putCache(
                    HttpCacheEntity(url, handle.etag, handle.lastModified, System.currentTimeMillis(), 0),
                )
            }
        }
    }

    companion object {
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_META_COUNT = "meta_count"
        const val KEY_FEATURED = "featured_json"
        private const val BATCH = 200
        private const val FULL_RESYNC_WINDOW_MS = 31L * 24 * 60 * 60 * 1000

        fun isOffline(result: SyncResult): Boolean = result is SyncResult.Failed
    }
}
