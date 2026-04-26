package com.mplayeraudio.services.mediasession

import android.content.Intent
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mplayeraudio.core.player.PlaybackPhase
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.services.mediasession.di.MediaSessionScopeQualifier
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.android.getKoin
import org.koin.core.qualifier.named

@OptIn(UnstableApi::class)
class PlayerMediaSessionService : MediaSessionService() {

    private var player: KitharaSimplePlayer? = null
    private var mediaSession: MediaSession? = null
    private var bridge: PlaybackQueueBridge? = null

    override fun onCreate() {
        super.onCreate()

        val koin = getKoin()
        val controller = koin.get<PlaybackQueueBridge>()
        val sessionPlayer = KitharaSimplePlayer(
            controller = controller,
            scope = koin.get<CoroutineScope>(named(MediaSessionScopeQualifier)),
            looper = Looper.getMainLooper(),
        )

        bridge = controller
        player = sessionPlayer
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setId(MediaSessionId)
            .setCallback(PlayerMediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPhase = bridge?.playbackState?.value?.phase
        if (currentPhase != PlaybackPhase.Playing) {
            bridge?.shutdown()
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        bridge?.shutdown()
        bridge = null
        super.onDestroy()
    }
}

private const val MediaSessionId = "multiplayer-media-session"
