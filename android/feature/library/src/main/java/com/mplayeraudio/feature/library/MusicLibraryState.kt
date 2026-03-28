package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.ui.components.MultiplayerCardSurfaceStyle

data class MusicLibraryState(
    val isLoading: Boolean = true,
    val content: MusicLibraryContent? = null,
)

data class MusicLibraryContent(
    val cards: List<MusicLibraryCard>,
)

sealed interface MusicLibraryCard {
    val key: String

    data class SavedTracks(
        val trackCount: Int,
    ) : MusicLibraryCard {
        override val key: String = SavedTracksCardKey
    }

    data class Playlist(
        val id: PlaylistId,
        val title: String,
        val trackCount: Int,
        val layout: PlaylistCardLayout,
        val style: MultiplayerCardSurfaceStyle,
        val artwork: PlaylistCardArtwork,
        val artworkSeed: Int,
    ) : MusicLibraryCard {
        override val key: String = id.toString()
    }
}

enum class PlaylistCardLayout {
    Compact,
    Featured,
}

enum class PlaylistCardArtwork {
    Default,
    Favourites,
}

const val SavedTracksCardKey = "saved-tracks-card"
