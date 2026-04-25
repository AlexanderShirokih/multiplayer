package com.mplayeraudio.services.userplaylists

import com.mplayeraudio.core.domain.musiclibrary.ArtistPreview
import com.mplayeraudio.core.domain.musiclibrary.MusicProvider
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRole
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.PlaylistTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import com.mplayeraudio.core.domain.musiclibrary.Track
import com.mplayeraudio.core.domain.musiclibrary.TrackPreview
import com.mplayeraudio.core.domain.musiclibrary.TrackRef
import com.mplayeraudio.core.domain.musiclibrary.UserPlaylistTrackId
import com.mplayeraudio.services.userplaylists.data.PlaylistWithTracks
import com.mplayeraudio.services.userplaylists.data.UserPlaylistDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class UserPlaylistsProvider(
    private val dao: UserPlaylistDao,
) : MusicProvider {

    override val id: MusicProviderId = MusicProviderId.UserPlaylists

    override fun observePlaylists(): Flow<List<PlaylistSummary>> {
        return dao.observePlaylistsWithTracks().map { playlists ->
            playlists.map { it.toSummary() }
        }
    }

    override fun observePlaylist(playlistId: PlaylistId): Flow<Playlist?> {
        return dao.observePlaylistWithTracks(playlistId.kind.value).map { playlistWithTracks ->
            playlistWithTracks?.toPlaylist()
        }
    }

    override fun observeSavedTracks(): Flow<SavedTracksResult> {
        return flowOf(SavedTracksResult.PrivateLibrary)
    }

    override fun observeAvailability(): Flow<com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability> {
        return flowOf(
            com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability(
                isAvailable = true,
                region = null,
                permissions = emptySet()
            )
        )
    }

    override suspend fun refreshAvailability() {
        // No-op
    }

    override suspend fun refreshPlaylists() {
        // No-op, data is local
    }

    override suspend fun refreshPlaylist(playlistId: PlaylistId) {
        // No-op, data is local
    }

    override suspend fun refreshSavedTracks() {
        // No-op, data is local
    }

    private fun PlaylistWithTracks.toSummary(): PlaylistSummary {
        return PlaylistSummary(
            id = PlaylistId(
                ownerId = ProviderUserId("local"),
                kind = PlaylistKind(playlist.id),
            ),
            provider = MusicProviderId.UserPlaylists,
            playlistUuid = null,
            title = playlist.title,
            ownerName = "Local",
            coverUriTemplate = null,
            trackCount = tracks.size,
            durationMs = null,
            isAvailable = true,
            isCollective = false,
            visibility = null,
            role = PlaylistRole.Regular,
        )
    }

    private fun PlaylistWithTracks.toPlaylist(): Playlist {
        return Playlist(
            summary = toSummary(),
            revision = null,
            snapshot = null,
            likesCount = null,
            tracks = tracks.mapIndexed { index, track ->
                val trackRef = TrackRef(
                    trackId = UserPlaylistTrackId(track.id),
                    albumId = null,
                )
                PlaylistTrackEntry(
                    position = index,
                    addedAt = track.addedAt.toString(),
                    originalIndex = index,
                    originalShuffleIndex = null,
                    isRecent = null,
                    trackRef = trackRef,
                    track = Track(
                        preview = TrackPreview(
                            ref = trackRef,
                            title = track.title,
                            artists = track.artist?.let { listOf(ArtistPreview(id = it, name = it)) } ?: emptyList(),
                            durationMs = null,
                            coverUriTemplate = null,
                            isAvailable = true,
                        ),
                        lyricsAvailable = false,
                        isAvailableForPremium = false,
                        isAvailableWithoutPermission = true,
                    )
                )
            }
        )
    }
}
