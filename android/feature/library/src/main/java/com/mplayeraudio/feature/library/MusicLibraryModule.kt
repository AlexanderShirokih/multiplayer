package com.mplayeraudio.feature.library

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun musicLibraryModule(): Module = module {
    factory { ObserveOwnPlaylistsUseCase(get()) }
    factory { ObserveSavedTracksUseCase(get()) }
    factory { RefreshLibraryUseCase(get()) }
    viewModel {
        MusicLibraryViewModel(
            observeOwnPlaylists = get(),
            observeSavedTracks = get(),
            refreshLibrary = get(),
        )
    }
}
