package com.mplayeraudio.services.kithara.di

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.core.player.PlayableUrlResolver
import com.mplayeraudio.services.kithara.CompositePlayableUrlResolver
import com.mplayeraudio.services.kithara.AudioPlaybackEngine
import com.mplayeraudio.services.kithara.KitharaAudioPlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val KitharaScopeQualifier = "kitharaPlaybackScope"

fun kitharaModule(): Module = module {
    single(named(KitharaScopeQualifier)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    single<AudioPlaybackEngine> {
        KitharaAudioPlaybackEngine(scope = get(named(KitharaScopeQualifier)))
    }
    single<PlayableUrlResolver> {
        CompositePlayableUrlResolver(
            providers = mapOf(
                MusicProviderId.YandexMusic to get<TrackStreamUrlProvider>(
                    named(MusicProviderId.YandexMusic.name),
                ),
                MusicProviderId.Device to get<TrackStreamUrlProvider>(
                    named(MusicProviderId.Device.name),
                ),
                MusicProviderId.UserPlaylists to get<TrackStreamUrlProvider>(
                    named(MusicProviderId.UserPlaylists.name),
                ),
            ),
        )
    }
}
