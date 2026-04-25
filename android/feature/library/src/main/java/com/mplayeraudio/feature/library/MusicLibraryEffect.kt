package com.mplayeraudio.feature.library

sealed interface MusicLibraryEffect {
    data class NavigateToTrackList(
        val destination: LibraryTrackListDestination,
    ) : MusicLibraryEffect

    data class NavigateToPlaylistEditor(
        val destination: LibraryTrackListDestination,
    ) : MusicLibraryEffect

    data class ShowError(
        val messageResId: Int? = null,
    ) : MusicLibraryEffect
}
