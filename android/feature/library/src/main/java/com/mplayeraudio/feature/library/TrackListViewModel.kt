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
import com.mplayeraudio.core.player.PlaybackError
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class LibraryTrackListDestination(
    val ref: PlaylistRef,
    val title: String,
    val role: PlaylistRole,
    val initiallyEditing: Boolean = false,
)

data class TrackListRouteState(
    val title: String = "",
    val tracks: List<TrackListItemState> = emptyList(),
    val activeTrackIndex: Int? = null,
    val playingTrackIndex: Int? = null,
    val isLoading: Boolean = true,
    val status: TrackListStatus? = null,
    val playbackError: PlaybackError? = null,
    val isAddingTrack: Boolean = false,
    val addTrackError: String? = null,
    val isEditing: Boolean = false,
    val canEdit: Boolean = false,
    val isDeletingPlaylist: Boolean = false,
    val playlistDeleted: Boolean = false,
    val playlistDeleteErrorMessage: String? = null,
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

private data class TrackListContentState(
    val title: String,
    val tracks: List<TrackListItemState> = emptyList(),
    val isLoading: Boolean = true,
    val status: TrackListStatus? = null,
    val isAddingTrack: Boolean = false,
    val addTrackError: String? = null,
    val isEditing: Boolean = false,
    val canEdit: Boolean = false,
    val isDeletingPlaylist: Boolean = false,
    val playlistDeleted: Boolean = false,
    val playlistDeleteErrorMessage: String? = null,
)

@Suppress("TooManyFunctions")
class TrackListViewModel(
    private val destination: LibraryTrackListDestination,
    private val refreshPlaylist: RefreshPlaylistUseCase,
    private val refreshSavedTracks: RefreshSavedTracksUseCase,
    private val playbackBridge: PlaybackQueueBridge,
    private val addTrackToPlaylist: AddUserPlaylistTrackUseCase,
    private val deletePlaylist: DeleteUserPlaylistUseCase,
    observePlaylist: ObservePlaylistUseCase,
    observeSavedTracks: ObserveSavedTracksUseCase,
) : ViewModel() {

    private val contentState = MutableStateFlow(
        TrackListContentState(
            title = destination.title,
            isLoading = true,
            isEditing = destination.initiallyEditing,
            canEdit = destination.ref.provider == MusicProviderId.UserPlaylists,
        ),
    )
    val state: StateFlow<TrackListRouteState> = combine(
        contentState,
        playbackBridge.playbackState,
    ) { content, playbackState ->
        content.toRouteState(playbackState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = contentState.value.toRouteState(PlaybackQueueState()),
    )

    init {
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

    fun onAcknowledgePlaybackError() {
        playbackBridge.acknowledgeError()
    }

    fun onClearAddTrackError() {
        contentState.update { it.copy(addTrackError = null) }
    }

    fun onClearDeletePlaylistError() {
        contentState.update { it.copy(playlistDeleteErrorMessage = null) }
    }

    fun onToggleEditing() {
        contentState.update { it.copy(isEditing = !it.isEditing) }
    }

    @Suppress("TooGenericExceptionCaught")
    fun onDeletePlaylist() {
        val current = contentState.value
        if (!current.canEdit || current.isDeletingPlaylist || current.playlistDeleted) return

        viewModelScope.launch {
            contentState.update {
                it.copy(isDeletingPlaylist = true, playlistDeleteErrorMessage = null)
            }
            try {
                deletePlaylist(destination.ref.id)
                playbackBridge.replaceQueue(queue = emptyList())
                contentState.update {
                    it.copy(
                        isDeletingPlaylist = false,
                        playlistDeleted = true,
                        isEditing = false,
                    )
                }
            } catch (e: Exception) {
                contentState.update {
                    it.copy(
                        isDeletingPlaylist = false,
                        playlistDeleteErrorMessage = e.message,
                    )
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun onAddTrackByUrl(url: String) {
        if (url.isBlank() || contentState.value.isAddingTrack) return

        viewModelScope.launch {
            contentState.update { it.copy(isAddingTrack = true, addTrackError = null) }
            try {
                val result = addTrackToPlaylist(destination.ref.id, url)
                when (result) {
                    is com.mplayeraudio.core.domain.musiclibrary.AddTrackResult.Success -> {
                        refresh()
                    }
                    is com.mplayeraudio.core.domain.musiclibrary.AddTrackResult.InvalidUrl -> {
                        contentState.update { it.copy(addTrackError = "InvalidUrl") }
                    }
                }
            } catch (e: Exception) {
                contentState.update { it.copy(addTrackError = e.message) }
            } finally {
                contentState.update { it.copy(isAddingTrack = false) }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            contentState.update { currentState ->
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
                contentState.update { currentState ->
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
                contentState.update { currentState ->
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
        val nextQueue = tracks.map(TrackListItemState::queueItem)
        if (contentState.value.shouldReplaceQueue(nextQueue)) {
            viewModelScope.launch {
                playbackBridge.replaceQueue(queue = nextQueue)
            }
        }
        contentState.update { currentState ->
            currentState.copy(
                title = title,
                tracks = tracks,
                isLoading = false,
                status = if (tracks.isEmpty()) TrackListStatus.Empty else null,
            )
        }
    }
}

private fun TrackListContentState.toRouteState(
    playbackState: PlaybackQueueState,
): TrackListRouteState {
    return TrackListRouteState(
        title = title,
        tracks = tracks,
        activeTrackIndex = tracks.activeTrackIndex(playbackState.activeItemId),
        playingTrackIndex = tracks.activeTrackIndex(playbackState.playingItemId),
        isLoading = isLoading,
        status = status,
        playbackError = playbackState.playbackError,
        isAddingTrack = isAddingTrack,
        addTrackError = addTrackError,
        isEditing = isEditing,
        canEdit = canEdit,
        isDeletingPlaylist = isDeletingPlaylist,
        playlistDeleted = playlistDeleted,
        playlistDeleteErrorMessage = playlistDeleteErrorMessage,
    )
}

private fun TrackListContentState.shouldReplaceQueue(
    nextQueue: List<PlaybackQueueItem>,
): Boolean {
    return tracks.map(TrackListItemState::queueItem) != nextQueue
}

private fun List<TrackListItemState>.activeTrackIndex(
    activeQueueItemId: String?,
): Int? {
    val index = indexOfFirst { track -> track.queueItem.id == activeQueueItemId }
    return index.takeIf { it >= 0 }
}

private fun PlaylistTrackEntry.toItemState(
    provider: MusicProviderId,
): TrackListItemState {
    val preview = track?.preview
    return TrackListItemState(
        queueItem = PlaybackQueueItem(
            id = trackQueueItemId(position = position, trackId = trackRef.trackId.stableKey),
            trackId = trackRef.trackId,
            source = toPlayableSource(provider = provider),
            title = preview?.title.orEmpty(),
            subtitle = preview?.artists.artistNames(),
            durationMs = preview?.durationMs ?: 0L,
            artworkUri = provider.resolveArtworkUri(preview?.coverUriTemplate),
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
            id = trackQueueItemId(position = position, trackId = trackRef.trackId.stableKey),
            trackId = trackRef.trackId,
            source = toPlayableSource(provider = provider),
            title = track?.title.orEmpty(),
            subtitle = track?.artists.artistNames(),
            durationMs = track?.durationMs ?: 0L,
            artworkUri = provider.resolveArtworkUri(track?.coverUriTemplate),
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

private fun toPlayableSource(
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

private fun MusicProviderId.resolveArtworkUri(template: String?): String? {
    if (template == null) return null

    return when (this) {
        MusicProviderId.YandexMusic -> template.replace(
            oldValue = YandexArtworkTemplateSizeToken,
            newValue = YandexArtworkSize,
        )
        MusicProviderId.Device -> template
        else -> template
    }
}

private const val SecondsPerMinute = 60L
private const val SecondsPerHour = 3_600L
private const val MillisecondsPerSecond = 1_000L
private const val YandexArtworkTemplateSizeToken = "%%"
private const val YandexArtworkSize = "400x400"
