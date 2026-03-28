package com.mplayeraudio.feature.library

sealed interface MusicLibraryEffect {
    data class NavigateToTrackList(
        val destination: LibraryTrackListDestination,
    ) : MusicLibraryEffect
}
