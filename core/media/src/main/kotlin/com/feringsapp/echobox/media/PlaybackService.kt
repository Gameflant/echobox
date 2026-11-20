package com.feringsapp.echobox.media

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** 后台播放服务：MediaSession + ExoPlayer，通知自己构建（超级岛 / 实时通知 / MediaStyle 三选一）。 */
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_PREV = "com.feringsapp.echobox.action.PREV"
        const val ACTION_TOGGLE = "com.feringsapp.echobox.action.TOGGLE"
        const val ACTION_NEXT = "com.feringsapp.echobox.action.NEXT"
    }

    private var mediaSession: MediaSession? = null
    private lateinit var notificationFactory: PlaybackNotificationFactory

    override fun onCreate() {
        super.onCreate()
        notificationFactory = PlaybackNotificationFactory(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        when (intent?.action) {
            ACTION_PREV -> player?.seekToPreviousMediaItem()
            ACTION_TOGGLE -> if (player != null) {
                if (player.isPlaying) player.pause() else player.play()
            }
            ACTION_NEXT -> player?.seekToNextMediaItem()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** 完全接管通知，避免 Media3 再发一条把超级岛顶掉。 */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        runCatching {
            val notification = notificationFactory.build(session)
            if (startInForegroundRequired) {
                startForeground(PlaybackNotificationFactory.NOTIFICATION_ID, notification)
            } else {
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager.notify(PlaybackNotificationFactory.NOTIFICATION_ID, notification)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
