package com.feringsapp.echobox.core.repo

import android.content.Context
import com.feringsapp.echobox.core.db.DownloadEntity
import com.feringsapp.echobox.core.db.EchoDatabase
import com.feringsapp.echobox.core.model.Track
import com.feringsapp.echobox.core.network.MirrorResolver
import com.feringsapp.echobox.core.network.SafeHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 下载到应用私有目录。
 *
 * - 任务挂在仓库自己的 scope 上，退出页面/切后台都不会中断
 * - 暂停 = 取消 OkHttp Call，保留文件与已下载字节
 * - 继续 = 用 `Range: bytes=<已下载>-` 断点续传，并且每次都重新解析当前选中的源，
 *   所以暂停后在设置里换源，继续时自动走新源
 */
class DownloadRepository(
    private val context: Context,
    private val db: EchoDatabase,
    private val http: SafeHttp,
    private val resolver: MirrorResolver,
) {
    data class Item(
        val trackId: String,
        val state: String,
        val bytesDone: Long,
        val bytesTotal: Long,
        val file: File?,
        val error: String?,
    ) {
        val done: Boolean get() = state == STATE_DONE
        val running: Boolean get() = state == STATE_DOWNLOADING
        val paused: Boolean get() = state == STATE_PAUSED
        val failed: Boolean get() = state == STATE_FAILED
        val active: Boolean get() = running || paused
        val progress: Float
            get() = if (bytesTotal <= 0) 0f else (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = ConcurrentHashMap<String, Job>()
    private val calls = ConcurrentHashMap<String, Call>()
    private val paused = ConcurrentHashMap.newKeySet<String>()

    fun dir(): File = File(context.filesDir, "downloads").apply { mkdirs() }

    fun observeAll(): Flow<List<Item>> =
        db.downloadDao().observeAll().map { rows -> rows.map { it.toItem() } }

    fun observe(trackId: String): Flow<Item?> =
        db.downloadDao().observe(trackId).map { it?.toItem() }

    /** 已下载且文件还在就返回本地文件。 */
    fun localFile(fileName: String): File? {
        if (fileName.isBlank()) return null
        val file = File(dir(), fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun isDownloading(trackId: String): Boolean = active.containsKey(trackId)

    /** 开始 / 继续下载，立即返回；重复调用不会起第二个任务。 */
    fun start(track: Track) {
        if (active.containsKey(track.id) && paused.contains(track.id)) {
            // 暂停中的任务已经被取消，允许重新起
            paused.remove(track.id)
        } else if (active.containsKey(track.id)) {
            return
        }
        paused.remove(track.id)
        active[track.id] = scope.launch {
            try {
                downloadInternal(track)
            } catch (_: CancellationException) {
                // 暂停时 intentionally 取消，状态已由 pause() 写入
            } catch (_: Exception) {
                // 失败状态已写库，界面自己展示
            } finally {
                active.remove(track.id)
                calls.remove(track.id)
            }
        }
    }

    /** 暂停：掐断连接、保留已下载字节。 */
    suspend fun pause(trackId: String, fileName: String) = withContext(Dispatchers.IO) {
        paused.add(trackId)
        calls.remove(trackId)?.let { runCatching { it.cancel() } }
        active.remove(trackId)?.cancel()
        val row = db.downloadDao().get(trackId)
        val file = File(dir(), fileName)
        val done = when {
            file.exists() -> file.length()
            else -> row?.bytesDone ?: 0L
        }
        val total = row?.bytesTotal ?: 0L
        if (row != null) {
            db.downloadDao().upsert(
                row.copy(
                    state = STATE_PAUSED,
                    bytesDone = done,
                    bytesTotal = if (total > 0) total else done,
                    updatedAtEpoch = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun download(track: Track): File = withContext(Dispatchers.IO) {
        downloadInternal(track)
        File(dir(), track.file)
    }

    private suspend fun downloadInternal(track: Track) {
        val file = File(dir(), track.file)
        val existing = if (file.exists() && file.length() > 0) file.length() else 0L
        // 每次开下都重新解析：暂停后在设置里换源，继续时就走新源
        val url = resolver.resolveAsset(track.file)
        val headers = if (existing > 0) mapOf("Range" to "bytes=$existing-") else emptyMap()
        val now = System.currentTimeMillis()

        suspend fun write(state: String, done: Long, total: Long, error: String? = null) {
            db.downloadDao().upsert(
                DownloadEntity(
                    trackId = track.id,
                    state = state,
                    filePath = if (state == STATE_DONE) file.absolutePath else null,
                    bytesDone = done,
                    bytesTotal = total,
                    etag = null,
                    error = error,
                    queueOrder = now,
                    updatedAtEpoch = System.currentTimeMillis(),
                ),
            )
        }

        write(STATE_DOWNLOADING, existing, 0L)

        var total = 0L
        var written = existing
        try {
            http.openRaw(url, headers, onCall = { calls[track.id] = it }).use { handle ->
                var startFrom = existing
                if (handle.code == 206) {
                    total = handle.contentRange
                        ?.substringAfterLast('/')
                        ?.toLongOrNull()
                        ?: -1L
                    if (total <= 0L) total = startFrom + handle.contentLength
                } else {
                    // 服务器没按 Range 返回：只能把已有内容丢掉从头写
                    if (startFrom > 0L) {
                        file.delete()
                        startFrom = 0L
                        written = 0L
                    }
                    total = handle.contentLength
                }
                write(STATE_DOWNLOADING, written, total)

                java.io.FileOutputStream(file, startFrom > 0L).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var sinceFlush = 0L
                    while (true) {
                        val read = handle.stream.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        sinceFlush += read
                        if (sinceFlush >= PROGRESS_FLUSH_BYTES) {
                            sinceFlush = 0L
                            write(STATE_DOWNLOADING, written, total)
                        }
                    }
                }
            }
            if (paused.contains(track.id)) {
                val done = if (file.exists()) file.length() else written
                write(STATE_PAUSED, done, total.coerceAtLeast(done))
                return
            }
            if (written <= 0L) throw IllegalStateException("下载到 0 字节，可能被镜像拦截")
            write(STATE_DONE, written, written)
        } catch (e: Exception) {
            val done = if (file.exists()) file.length() else written
            if (paused.contains(track.id)) {
                write(STATE_PAUSED, done, total.coerceAtLeast(done))
                return
            }
            file.delete()
            write(
                STATE_FAILED,
                0L,
                total.coerceAtLeast(0L),
                (e.message ?: e::class.simpleName ?: "下载失败").take(300),
            )
            throw e
        }
    }

    suspend fun delete(trackId: String, fileName: String) = withContext(Dispatchers.IO) {
        paused.remove(trackId)
        calls.remove(trackId)?.let { runCatching { it.cancel() } }
        active.remove(trackId)?.cancel()
        // 取消后稍等一下让文件句柄释放
        delay(80)
        File(dir(), fileName).delete()
        db.downloadDao().delete(trackId)
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        dir().listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun DownloadEntity.toItem(): Item = Item(
        trackId = trackId,
        state = state,
        bytesDone = bytesDone,
        bytesTotal = bytesTotal,
        file = filePath?.let { File(it) }?.takeIf { it.exists() },
        error = error,
    )

    companion object {
        const val STATE_DOWNLOADING = "DOWNLOADING"
        const val STATE_PAUSED = "PAUSED"
        const val STATE_DONE = "DONE"
        const val STATE_FAILED = "FAILED"
        private const val PROGRESS_FLUSH_BYTES = 512L * 1024
    }
}
