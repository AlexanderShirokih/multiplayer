package com.mplayeraudio.feature.library

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun musicLibraryModule(): Module = module {
    factory { ObserveOwnPlaylistsUseCase(get()) }
    factory { RefreshLibraryUseCase(get()) }
    factory { ObservePlaylistUseCase(get()) }
    factory { RefreshPlaylistUseCase(get()) }
    factory { ObserveSavedTracksUseCase(get()) }
    factory { RefreshSavedTracksUseCase(get()) }
    factory { CreateUserPlaylistUseCase(get()) }
    factory { AddUserPlaylistTrackUseCase(get()) }
    viewModel {
        MusicLibraryViewModel(
            observeOwnPlaylists = get(),
            refreshLibrary = get(),
            createPlaylist = get(),
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
            addTrackToPlaylist = get(),
        )
    }
}
