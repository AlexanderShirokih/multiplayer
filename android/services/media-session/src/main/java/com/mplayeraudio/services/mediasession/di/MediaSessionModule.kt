package com.mplayeraudio.services.mediasession.di

import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.services.mediasession.MediaControllerPlaybackQueueBridge
import com.mplayeraudio.services.mediasession.mediaSessionBridgeScope
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val MediaSessionScopeQualifier = "mediaSessionScope"

fun mediaSessionModule(): Module = module {
    single(named(MediaSessionScopeQualifier)) {
        mediaSessionBridgeScope()
    }
    single<PlaybackQueueBridge> {
        MediaControllerPlaybackQueueBridge(
            context = get(),
            scope = get<CoroutineScope>(named(MediaSessionScopeQualifier)),
        )
    }
}
