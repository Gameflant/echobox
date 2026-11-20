package com.feringsapp.echobox.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.session.MediaSession

/**
 * 播放通知统一走 Android 16 的实时通知（ProgressStyle + 常驻更新），
 * 带上一首 / 播放暂停 / 下一首三个按钮。不再走超级岛。
 */
class PlaybackNotificationFactory(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "playback_v3"
        const val NOTIFICATION_ID = 1001
    }

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "播放控制",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "播放、暂停与切歌"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun build(session: MediaSession): Notification {
        val player = session.player
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString().orEmpty().ifBlank { "EchoBox" }
        val artist = metadata?.artist?.toString().orEmpty()
        val album = metadata?.albumTitle?.toString().orEmpty()
        val subtitle = listOf(artist, album)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .ifBlank { "EchoBox" }
        val playing = player.isPlaying
        val duration = player.duration.coerceAtLeast(0L)
        val position = player.currentPosition.coerceAtLeast(0L)

        val contentIntent = session.sessionActivity ?: buildOpenAppIntent()

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.feringsapp.echobox.media.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        CoverCache.get(metadata?.artworkUri?.toString())?.let { builder.setLargeIcon(it) }

        val canPromote = Build.VERSION.SDK_INT >= 36 && duration > 0
        if (canPromote) {
            applyLiveUpdate(builder, position, duration, playing)
        } else {
            // 系统不支持实时通知时，至少带上进度，避免看起来像静止卡片
            if (duration > 0) {
                builder.setProgress(100, (position * 100 / duration).toInt().coerceIn(0, 100), false)
            }
        }

        builder
            .addAction(action(PlaybackService.ACTION_PREV, 3, "上一首"))
            .addAction(action(PlaybackService.ACTION_TOGGLE, 1, if (playing) "暂停" else "播放"))
            .addAction(action(PlaybackService.ACTION_NEXT, 2, "下一首"))

        return builder.build()
    }

    @RequiresApi(36)
    private fun applyLiveUpdate(
        builder: Notification.Builder,
        position: Long,
        duration: Long,
        playing: Boolean,
    ) {
        val percent = (position * 100 / duration).toInt().coerceIn(0, 100)
        val style = Notification.ProgressStyle()
            .setProgress(percent)
        builder.setStyle(style)
        builder.setRequestPromotedOngoing(true)
        builder.setShortCriticalText(if (playing) "播放中" else "已暂停")
    }

    private fun action(actionName: String, requestCode: Int, label: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, com.feringsapp.echobox.media.R.drawable.ic_notification),
            label,
            serviceIntent(actionName, requestCode),
        ).build()

    /** 直接唤起 PlaybackService 执行动作，绕开 MediaButtonReceiver 只放行 play 的限制。 */
    private fun serviceIntent(actionName: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).setAction(actionName)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildOpenAppIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
