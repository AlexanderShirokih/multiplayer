package com.mplayeraudio.services.devicemusic

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.mplayeraudio.core.domain.musiclibrary.MusicProvider
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRole
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.PlaylistVisibility
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions")
internal class DeviceMusicProvider(
    private val context: Context,
) : MusicProvider {
    override val id: MusicProviderId = MusicProviderId.Device

    private val contentResolver: ContentResolver = context.contentResolver
    private val availabilityState = MutableStateFlow(
        MusicServiceAvailability(
            isAvailable = true,
            region = null,
            permissions = emptySet(),
        ),
    )
    private val playlistState = MutableStateFlow(devicePlaylist(tracks = emptyList()))

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshIfPermitted()
        }
    }

    init {
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }

    override fun observeAvailability(): Flow<MusicServiceAvailability> = availabilityState

    override fun observePlaylists(): Flow<List<PlaylistSummary>> {
        return playlistState.map { playlist -> listOf(playlist.summary) }
    }

    override fun observePlaylist(id: PlaylistId): Flow<Playlist?> {
        return playlistState.map { playlist ->
            playlist.takeIf { id == devicePlaylistId }
        }
    }

    override fun observeSavedTracks(): Flow<SavedTracksResult> = flowOf(SavedTracksResult.PrivateLibrary)

    override suspend fun refreshAvailability() = Unit

    override suspend fun refreshPlaylists() {
        publishPlaylist()
    }

    override suspend fun refreshPlaylist(id: PlaylistId) {
        if (id == devicePlaylistId) {
            publishPlaylist()
        }
    }

    override suspend fun refreshSavedTracks() = Unit

    private fun refreshIfPermitted() {
        if (!MediaAudioPermission.hasPermission(context)) {
            return
        }

        playlistState.value = queryPlaylist()
    }

    private fun publishPlaylist() {
        playlistState.value = if (MediaAudioPermission.hasPermission(context)) {
            queryPlaylist()
        } else {
            devicePlaylist(tracks = emptyList())
        }
    }

    private fun queryPlaylist(): Playlist {
        val tracks = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            DeviceMediaStoreMapper.projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use(DeviceMediaStoreMapper::map).orEmpty()

        return devicePlaylist(
            tracks = tracks.map(DeviceAudioTrack::entry),
        )
    }

    private fun devicePlaylist(
        tracks: List<com.mplayeraudio.core.domain.musiclibrary.PlaylistTrackEntry>,
    ): Playlist {
        return Playlist(
            summary = PlaylistSummary(
                id = devicePlaylistId,
                provider = MusicProviderId.Device,
                playlistUuid = null,
                title = context.getString(R.string.device_music_playlist_title),
                ownerName = null,
                coverUriTemplate = null,
                trackCount = tracks.size,
                durationMs = tracks.mapNotNull { it.track?.preview?.durationMs }.sum().takeIf { it > 0L },
                isAvailable = true,
                isCollective = false,
                visibility = PlaylistVisibility.Private,
                role = PlaylistRole.Regular,
            ),
            revision = null,
            snapshot = null,
            likesCount = null,
            tracks = tracks,
        )
    }
}

val devicePlaylistId = PlaylistId(
    ownerId = ProviderUserId("device"),
    kind = PlaylistKind(0L),
)
