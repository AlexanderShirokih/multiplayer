package com.mplayeraudio.services.userplaylists

import com.mplayeraudio.core.domain.musiclibrary.AddTrackResult
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRef
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.UserPlaylistsRepository
import com.mplayeraudio.core.domain.musiclibrary.UserPlaylistTrackId
import com.mplayeraudio.services.userplaylists.data.UserPlaylistDao
import com.mplayeraudio.services.userplaylists.data.UserPlaylistEntity
import com.mplayeraudio.services.userplaylists.data.UserPlaylistTrackEntity

class UserPlaylistsRepositoryImpl(
    private val dao: UserPlaylistDao,
    private val urlMetadataExtractor: UrlMetadataExtractor,
) : UserPlaylistsRepository {

    override suspend fun createPlaylist(): PlaylistRef {
        val maxId = dao.getMaxPlaylistId() ?: 0L
        val nextId = maxId + 1
        
        val title = if (nextId == 1L) {
            "Новый плейлист"
        } else {
            "Новый плейлист $nextId"
        }
        
        val entity = UserPlaylistEntity(
            title = title,
            createdAt = System.currentTimeMillis(),
        )
        
        val id = dao.insertPlaylist(entity)
        
        return PlaylistRef(
            provider = MusicProviderId.UserPlaylists,
            id = PlaylistId(
                ownerId = ProviderUserId("local"),
                kind = PlaylistKind(id),
            )
        )
    }

    override suspend fun addTrackByUrl(playlistId: PlaylistId, url: String): AddTrackResult {
        val metadata = urlMetadataExtractor.parse(url) ?: return AddTrackResult.InvalidUrl
        
        val trackEntity = UserPlaylistTrackEntity(
            playlistId = playlistId.kind.value,
            url = url,
            title = metadata.title,
            artist = metadata.artist,
            addedAt = System.currentTimeMillis(),
        )
        
        dao.insertTrack(trackEntity)
        return AddTrackResult.Success
    }

    override suspend fun deleteTrack(playlistId: PlaylistId, trackId: TrackId) {
        val userPlaylistTrackId = trackId as? UserPlaylistTrackId ?: return
        dao.deleteTrack(playlistId.kind.value, userPlaylistTrackId.value)
    }

    override suspend fun deletePlaylist(playlistId: PlaylistId) {
        dao.deletePlaylist(playlistId.kind.value)
    }
}
