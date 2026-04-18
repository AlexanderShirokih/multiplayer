package com.mplayeraudio.services.mediasession

import android.content.Intent
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mplayeraudio.core.player.PlayableUrlResolver
import com.mplayeraudio.services.kithara.AudioPlaybackEngine
import com.mplayeraudio.services.mediasession.di.MediaSessionScopeQualifier
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.android.getKoin
import org.koin.core.qualifier.named

@OptIn(UnstableApi::class)
class PlayerMediaSessionService : MediaSessionService() {

    private var player: KitharaSimplePlayer? = null
    private var mediaSession: MediaSession? = null
    private var cachingResolver: CachingPlayableUrlResolver? = null

    override fun onCreate() {
        super.onCreate()

        val koin = getKoin()
        val resolver = CachingPlayableUrlResolver(
            delegate = koin.get<PlayableUrlResolver>(),
        )
        val sessionPlayer = KitharaSimplePlayer(
            context = applicationContext,
            engine = koin.get<AudioPlaybackEngine>(),
            urlResolver = resolver,
            scope = koin.get<CoroutineScope>(named(MediaSessionScopeQualifier)),
            looper = Looper.getMainLooper(),
            invalidateUrlCache = resolver::invalidate,
        )

        cachingResolver = resolver
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
        val sessionPlayer = player
        if (sessionPlayer == null || !sessionPlayer.isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        cachingResolver?.clear()
        cachingResolver = null
        super.onDestroy()
    }
}

private const val MediaSessionId = "multiplayer-media-session"
