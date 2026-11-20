package com.feringsapp.echobox.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 入队用的最小信息：只要播放器认得的都放这里，界面不再自己拼 URI。 */
data class QueueItem(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String,
    /** 歌词全文（LRC），没有歌词就是空串。 */
    val lyric: String = "",
    /** 封面地址（https），没有就空串。 */
    val coverUrl: String = "",
    /** 专辑（系列）名，通知里跟在歌手后面。 */
    val album: String = "",
)

data class PlaybackUiState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val connected: Boolean = false,
    val trackId: String = "",
    val lyric: String = "",
    val currentIndex: Int = 0,
    val queueSize: Int = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val coverUrl: String = "",
) {
    val hasTrack: Boolean get() = title.isNotBlank() || trackId.isNotBlank()
}

/** 循环模式三态，界面直接按这个顺序轮转。 */
enum class RepeatMode { OFF, ALL, ONE }

/**
 * 与 PlaybackService 里的 MediaSession 建立连接，并把播放状态暴露成 StateFlow。
 * 队列、上一首/下一首、循环都走 MediaController 自己的能力，不在这里另造一套。
 */
class PlaybackController(
    private val context: Context,
    private val queueStore: QueueStore? = null,
) {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var ticksSincePersist = 0

    /** 恢复队列时用 trackId 重新解析播放地址；下载文件被删时会退回在线源。 */
    var uriResolver: ((String) -> String?)? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let { sync(it) }
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    controller = future.get().also { it.addListener(listener) }
                    sync(controller)
                    startTicker()
                    restoreIfNeeded()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** 单曲：等价于只有一个元素、从 0 开始的队列。 */
    fun play(title: String, artist: String, url: String) {
        playQueue(listOf(QueueItem(id = url, title = title, artist = artist, uri = url)), 0)
    }

    fun playQueue(items: List<QueueItem>, startIndex: Int) {
        val c = controller ?: return
        if (items.isEmpty()) return
        val mediaItems = items.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(items.indices)
        c.setMediaItems(mediaItems, safeIndex, 0L)
        c.prepare()
        c.play()
        _state.value = _state.value.copy(
            title = items[safeIndex].title,
            artist = items[safeIndex].artist,
            trackId = items[safeIndex].id,
            lyric = items[safeIndex].lyric,
            currentIndex = safeIndex,
            queueSize = items.size,
        )
        persist()
    }

    /** 当前队列快照，界面据此渲染播放列表。 */
    fun queueItems(): List<QueueItem> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).mapNotNull { i ->
            val item = c.getMediaItemAt(i) ?: return@mapNotNull null
            val meta = item.mediaMetadata
            QueueItem(
                id = item.mediaId,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                uri = item.localConfiguration?.uri?.toString().orEmpty(),
                coverUrl = meta.artworkUri?.toString().orEmpty(),
            )
        }
    }

    /** 跳播队列里的第 index 首。 */
    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(index)
        c.play()
        persist()
    }

    fun removeAt(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.removeMediaItem(index)
        persist()
    }

    /** 追加到队列末尾。 */
    fun append(items: List<QueueItem>) {
        val c = controller ?: return
        if (items.isEmpty()) return
        c.addMediaItems(items.map { it.toMediaItem() })
        persist()
    }

    /** 插到当前曲目后面，就是「下一首播放」。 */
    fun insertNext(items: List<QueueItem>) {
        val c = controller ?: return
        if (items.isEmpty()) return
        val at = (c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount)
        c.addMediaItems(at, items.map { it.toMediaItem() })
        persist()
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        persist()
    }

    /** 把当前队列和位置写盘。变化点与定时器都会调用。 */
    fun persist() {
        val store = queueStore ?: return
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            scope.launch(Dispatchers.IO) { runCatching { store.clear() } }
            return
        }
        val snapshot = queueItems()
        val index = c.currentMediaItemIndex.coerceAtLeast(0)
        val position = c.currentPosition.coerceAtLeast(0L)
        val repeat = c.repeatMode
        scope.launch(Dispatchers.IO) {
            runCatching { store.save(snapshot, index, position, repeat) }
        }
    }

    /** 启动时恢复上次的队列，只准备不自动播放。 */
    private fun restoreIfNeeded() {
        val store = queueStore ?: return
        scope.launch {
            val persisted = store.load() ?: return@launch
            if (persisted.items.isEmpty()) return@launch
            val c = controller ?: return@launch
            if (c.mediaItemCount > 0) return@launch
            // MainShell 可能比 controller 晚一步注入 resolver，等一小会儿
            if (uriResolver == null) delay(800)
            val items = store.toQueueItems(persisted).map { item ->
                val fresh = uriResolver?.invoke(item.id)
                if (!fresh.isNullOrBlank()) item.copy(uri = fresh) else item
            }
            val mediaItems = items.map { it.toMediaItem() }
            val index = persisted.index.coerceIn(mediaItems.indices)
            runCatching {
                c.setMediaItems(mediaItems, index, persisted.positionMs)
                c.repeatMode = persisted.repeatMode
                c.prepare()
                sync(c)
            }
        }
    }

    private fun QueueItem.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                if (coverUrl.isNotBlank()) setArtworkUri(Uri.parse(coverUrl))
                if (album.isNotBlank()) setAlbumTitle(album)
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun cycleRepeat(): RepeatMode {
        val c = controller ?: return RepeatMode.OFF
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        _state.value = _state.value.copy(repeatMode = next)
        return when (next) {
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        scope.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun startTicker() {
        if (ticker != null) return
        ticker = scope.launch {
            while (isActive) {
                controller?.let { player ->
                    if (player.isConnected) {
                        _state.value = _state.value.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L),
                            isPlaying = player.isPlaying,
                            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                            hasNext = player.hasNextMediaItem(),
                            hasPrevious = player.hasPreviousMediaItem(),
                            repeatMode = player.repeatMode,
                        )
                    }
                }
                ticksSincePersist++
                if (queueStore != null && controller?.isPlaying == true && ticksSincePersist >= 33) {
                    ticksSincePersist = 0
                    persist()
                }
                delay(300)
            }
        }
    }

    private fun sync(player: Player?) {
        if (player == null) return
        val metadata = player.currentMediaItem?.mediaMetadata
        _state.value = PlaybackUiState(
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            connected = true,
            trackId = player.currentMediaItem?.mediaId.orEmpty(),
            lyric = _state.value.lyric,
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            queueSize = player.mediaItemCount,
            repeatMode = player.repeatMode,
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem(),
            coverUrl = metadata?.artworkUri?.toString().orEmpty(),
        )
    }
}
