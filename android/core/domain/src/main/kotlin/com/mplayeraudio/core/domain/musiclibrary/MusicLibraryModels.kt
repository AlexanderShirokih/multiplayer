package com.mplayeraudio.core.domain.musiclibrary

enum class MusicProviderId {
    Device,
    YandexMusic,
    UserPlaylists,
}

@JvmInline
value class ProviderUserId(val value: String)

@JvmInline
value class PlaylistKind(val value: Long)

@JvmInline
value class PlaylistUuid(val value: String)

sealed interface TrackId {
    val stableKey: String
}

@JvmInline
value class YandexTrackId(val value: String) : TrackId {
    override val stableKey: String
        get() = value
}

@JvmInline
value class DeviceTrackId(val value: Long) : TrackId {
    override val stableKey: String
        get() = "device:$value"
}

@JvmInline
value class UserPlaylistTrackId(val value: Long) : TrackId {
    override val stableKey: String
        get() = "user-playlist:$value"
}

@JvmInline
value class AlbumId(val value: String)

data class PlaylistId(
    val ownerId: ProviderUserId,
    val kind: PlaylistKind,
)

data class PlaylistRef(
    val provider: MusicProviderId,
    val id: PlaylistId,
)

data class TrackRef(
    val trackId: TrackId,
    val albumId: AlbumId?,
)

enum class PlaylistVisibility {
    Public,
    Private,
}

enum class PlaylistRole {
    Favourites,
    Regular,
}

data class PlaylistSummary(
    val id: PlaylistId,
    val provider: MusicProviderId,
    val playlistUuid: PlaylistUuid?,
    val title: String,
    val ownerName: String?,
    val coverUriTemplate: String?,
    val trackCount: Int,
    val durationMs: Long?,
    val isAvailable: Boolean,
    val isCollective: Boolean,
    val visibility: PlaylistVisibility?,
    val role: PlaylistRole = PlaylistRole.Regular,
)

data class Playlist(
    val summary: PlaylistSummary,
    val revision: Long?,
    val snapshot: Long?,
    val likesCount: Int?,
    val tracks: List<PlaylistTrackEntry>,
)

data class PlaylistTrackEntry(
    val position: Int,
    val addedAt: String?,
    val originalIndex: Int?,
    val originalShuffleIndex: Int?,
    val isRecent: Boolean?,
    val trackRef: TrackRef,
    val track: Track?,
)

data class SavedTracks(
    val ownerId: ProviderUserId,
    val revision: Long?,
    val tracks: List<SavedTrackEntry>,
)

data class SavedTrackEntry(
    val position: Int,
    val addedAt: String?,
    val trackRef: TrackRef,
    val track: TrackPreview?,
)

data class TrackPreview(
    val ref: TrackRef,
    val title: String,
    val artists: List<ArtistPreview>,
    val durationMs: Long?,
    val coverUriTemplate: String?,
    val isAvailable: Boolean,
)

data class Track(
    val preview: TrackPreview,
    val lyricsAvailable: Boolean,
    val isAvailableForPremium: Boolean,
    val isAvailableWithoutPermission: Boolean,
)

data class ArtistPreview(
    val id: String,
    val name: String,
)

data class MusicServiceAvailability(
    val isAvailable: Boolean,
    val region: Int?,
    val permissions: Set<String>,
)

sealed interface SavedTracksResult {
    data class Available(
        val value: SavedTracks,
    ) : SavedTracksResult

    data object PrivateLibrary : SavedTracksResult
}

sealed interface MusicLibraryError

sealed class MusicLibraryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause), MusicLibraryError {

    class Unauthorized : MusicLibraryException(
        message = "Yandex Music access token is invalid or expired.",
    )

    data class ServiceUnavailable(
        val description: String?,
    ) : MusicLibraryException(
        message = description ?: "Yandex Music service is unavailable in the current environment.",
    )

    data class ProviderError(
        val code: String,
        val description: String?,
    ) : MusicLibraryException(
        message = description ?: code,
    )

    data class InvalidResponse(
        val description: String,
    ) : MusicLibraryException(
        message = description,
    )
}
