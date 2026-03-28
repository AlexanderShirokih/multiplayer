package com.mplayeraudio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRole
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.ui.components.MultiplayerCardSurfaceStyle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val PlaylistCardStyles = listOf(
    MultiplayerCardSurfaceStyle.Surface2,
    MultiplayerCardSurfaceStyle.Surface3,
    MultiplayerCardSurfaceStyle.Surface4,
)

class MusicLibraryViewModel(
    observeOwnPlaylists: ObserveOwnPlaylistsUseCase,
    private val refreshLibrary: RefreshLibraryUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MusicLibraryState())
    val state: StateFlow<MusicLibraryState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MusicLibraryEffect>()
    val effects: SharedFlow<MusicLibraryEffect> = _effects.asSharedFlow()

    init {
        observeOwnPlaylists()
            .onEach { playlists ->
            val orderedPlaylists = playlists.orderedForPresentation()
            val cards = buildList {
                orderedPlaylists.mapIndexedTo(this) { index, playlist ->
                    playlist.toLibraryCard(index)
                }
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    content = MusicLibraryContent(
                        cards = cards,
                    ),
                )
            }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                refreshLibrary()
            } catch (_: MusicLibraryException) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onPlaylistClick(playlistId: PlaylistId) {
        viewModelScope.launch {
            _effects.emit(MusicLibraryEffect.NavigateToPlaylist(playlistId))
        }
    }
}

private fun PlaylistSummary.toLibraryCard(
    index: Int,
): MusicLibraryCard.Playlist {
    val isFavourites = role == PlaylistRole.Favourites
    val presentationSeed = libraryPresentationSeed()

    return MusicLibraryCard.Playlist(
        id = id,
        title = title,
        trackCount = trackCount,
        layout = if (isFeaturedCard(index)) {
            PlaylistCardLayout.Featured
        } else {
            PlaylistCardLayout.Compact
        },
        style = if (isFavourites) {
            MultiplayerCardSurfaceStyle.Surface4
        } else {
            PlaylistCardStyles[Math.floorMod(presentationSeed, PlaylistCardStyles.size)]
        },
        artwork = if (isFavourites) {
            PlaylistCardArtwork.Favourites
        } else {
            PlaylistCardArtwork.Default
        },
        artworkSeed = presentationSeed,
    )
}

private fun List<PlaylistSummary>.orderedForPresentation(): List<PlaylistSummary> {
    val favourites = firstOrNull { it.role == PlaylistRole.Favourites }
        ?: return this
    val regularPlaylists = filterNot { it.role == PlaylistRole.Favourites }

    return buildList {
        regularPlaylists.firstOrNull()?.let(::add)
        add(favourites)
        regularPlaylists.drop(1).forEach(::add)
    }
}

private fun PlaylistSummary.libraryPresentationSeed(): Int {
    return playlistUuid?.value?.hashCode() ?: id.hashCode()
}

private fun isFeaturedCard(index: Int): Boolean {
    return index >= FirstFeaturedCardIndex && index % FeaturedCardPeriod == FirstFeaturedCardIndex
}

private const val FirstFeaturedCardIndex = 2
private const val FeaturedCardPeriod = 3
