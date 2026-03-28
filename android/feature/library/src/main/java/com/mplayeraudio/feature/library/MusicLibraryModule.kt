package com.mplayeraudio.feature.library

import com.mplayeraudio.core.player.PlaybackQueueBridge
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun musicLibraryModule(): Module = module {
    single<PlaybackQueueBridge> { InMemoryPlaybackQueueBridge() }
    factory { ObserveOwnPlaylistsUseCase(get()) }
    factory { RefreshLibraryUseCase(get()) }
    factory { ObservePlaylistUseCase(get()) }
    factory { RefreshPlaylistUseCase(get()) }
    factory { ObserveSavedTracksUseCase(get()) }
    factory { RefreshSavedTracksUseCase(get()) }
    viewModel {
        MusicLibraryViewModel(
            observeOwnPlaylists = get(),
            refreshLibrary = get(),
        )
    }
    viewModel { params ->
        TrackListViewModel(
            destination = params.get(),
            observePlaylist = get(),
            refreshPlaylist = get(),
            observeSavedTracks = get(),
            refreshSavedTracks = get(),
            playbackBridge = get(),
        )
    }
}
