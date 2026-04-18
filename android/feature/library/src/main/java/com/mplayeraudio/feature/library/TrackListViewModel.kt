package com.mplayeraudio.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRef
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRole
import com.mplayeraudio.core.domain.musiclibrary.PlaylistTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.SavedTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class LibraryTrackListDestination(
    val ref: PlaylistRef,
    val title: String,
    val role: PlaylistRole,
)

data class TrackListRouteState(
    val title: String = "",
    val tracks: List<TrackListItemState> = emptyList(),
    val activeTrackIndex: Int? = null,
    val isLoading: Boolean = true,
    val status: TrackListStatus? = null,
)

data class TrackListItemState(
    val queueItem: PlaybackQueueItem,
    val title: String,
    val artist: String,
    val duration: String,
    val trackPosition: Int,
)

enum class TrackListStatus {
    Empty,
    PrivateLibrary,
    GenericError,
}

class TrackListViewModel(
    private val destination: LibraryTrackListDestination,
    private val refreshPlaylist: RefreshPlaylistUseCase,
    private val refreshSavedTracks: RefreshSavedTracksUseCase,
    private val playbackBridge: PlaybackQueueBridge,
    observePlaylist: ObservePlaylistUseCase,
    observeSavedTracks: ObserveSavedTracksUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TrackListRouteState(
            title = destination.title,
            isLoading = true,
        ),
    )
    val state: StateFlow<TrackListRouteState> = _state.asStateFlow()

    init {
        playbackBridge.playbackState
            .onEach { playbackState ->
                _state.update { currentState ->
                    currentState.copy(activeTrackIndex = playbackState.currentIndex)
                }
            }
            .launchIn(viewModelScope)

        when (destination.role) {
            PlaylistRole.Favourites -> observeSavedTracks()
                .onEach(::consumeSavedTracks)
                .launchIn(viewModelScope)

            PlaylistRole.Regular -> observePlaylist(destination.ref)
                .onEach { playlist ->
                    playlist ?: return@onEach
                    consumePlaylist(playlist)
                }
                .launchIn(viewModelScope)
        }

        refresh()
    }

    fun onTrackClick(index: Int) {
        viewModelScope.launch {
            playbackBridge.playTrack(index)
        }
    }

    fun onRetry() {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    isLoading = true,
                    status = null,
                )
            }
            try {
                when (destination.role) {
                    PlaylistRole.Favourites -> refreshSavedTracks()
                    PlaylistRole.Regular -> refreshPlaylist(destination.ref)
                }
            } catch (_: Exception) {
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        status = currentState.status ?: TrackListStatus.GenericError,
                    )
                }
            }
        }
    }

    private fun consumePlaylist(playlist: Playlist) {
        publishContent(
            title = playlist.summary.title,
            tracks = playlist.tracks.map { entry ->
                entry.toItemState(provider = destination.ref.provider)
            },
        )
    }

    private fun consumeSavedTracks(result: SavedTracksResult) {
        when (result) {
            is SavedTracksResult.Available -> {
                publishContent(
                    title = destination.title,
                    tracks = result.value.tracks.map { entry ->
                        entry.toItemState(provider = destination.ref.provider)
                    },
                )
            }

            SavedTracksResult.PrivateLibrary -> {
                viewModelScope.launch {
                    playbackBridge.replaceQueue(queue = emptyList())
                }
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        tracks = emptyList(),
                        status = TrackListStatus.PrivateLibrary,
                    )
                }
            }
        }
    }

    private fun publishContent(
        title: String,
        tracks: List<TrackListItemState>,
    ) {
        viewModelScope.launch {
            playbackBridge.replaceQueue(queue = tracks.map(TrackListItemState::queueItem))
        }
        _state.update { currentState ->
            currentState.copy(
                title = title,
                tracks = tracks,
                isLoading = false,
                status = if (tracks.isEmpty()) TrackListStatus.Empty else null,
            )
        }
    }
}

private fun PlaylistTrackEntry.toItemState(
    provider: MusicProviderId,
): TrackListItemState {
    val preview = track?.preview
    return TrackListItemState(
        queueItem = PlaybackQueueItem(
            id = trackQueueItemId(position = position, trackId = trackRef.trackId.value),
            trackId = trackRef.trackId,
            source = trackRef.trackId.toPlayableSource(provider = provider),
            title = preview?.title.orEmpty(),
            subtitle = preview?.artists.artistNames(),
            durationMs = preview?.durationMs ?: 0L,
        ),
        title = preview?.title.orEmpty(),
        artist = preview?.artists.artistNames(),
        duration = formatTrackDuration(preview?.durationMs),
        trackPosition = position + 1,
    )
}

private fun SavedTrackEntry.toItemState(
    provider: MusicProviderId,
): TrackListItemState {
    return TrackListItemState(
        queueItem = PlaybackQueueItem(
            id = trackQueueItemId(position = position, trackId = trackRef.trackId.value),
            trackId = trackRef.trackId,
            source = trackRef.trackId.toPlayableSource(provider = provider),
            title = track?.title.orEmpty(),
            subtitle = track?.artists.artistNames(),
            durationMs = track?.durationMs ?: 0L,
        ),
        title = track?.title.orEmpty(),
        artist = track?.artists.artistNames(),
        duration = formatTrackDuration(track?.durationMs),
        trackPosition = position + 1,
    )
}

private fun trackQueueItemId(
    position: Int,
    trackId: String,
): String {
    return "$position:$trackId"
}

private fun com.mplayeraudio.core.domain.musiclibrary.TrackId.toPlayableSource(
    provider: MusicProviderId,
): PlayableSource {
    return PlayableSource.Remote(provider)
}

private fun formatTrackDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0L) return "0:00"

    val totalSeconds = durationMs / MillisecondsPerSecond
    val hours = totalSeconds / SecondsPerHour
    val minutes = (totalSeconds % SecondsPerHour) / SecondsPerMinute
    val seconds = totalSeconds % SecondsPerMinute

    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

private fun List<com.mplayeraudio.core.domain.musiclibrary.ArtistPreview>?.artistNames(): String {
    return this.orEmpty()
        .joinToString(separator = ", ", transform = com.mplayeraudio.core.domain.musiclibrary.ArtistPreview::name)
}

private const val SecondsPerMinute = 60L
private const val SecondsPerHour = 3_600L
private const val MillisecondsPerSecond = 1_000L
