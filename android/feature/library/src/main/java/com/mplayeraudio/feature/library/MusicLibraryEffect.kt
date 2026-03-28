package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.PlaylistId

sealed interface MusicLibraryEffect {
    data class NavigateToPlaylist(val playlistId: PlaylistId) : MusicLibraryEffect
    data object NavigateToSavedTracks : MusicLibraryEffect
}
