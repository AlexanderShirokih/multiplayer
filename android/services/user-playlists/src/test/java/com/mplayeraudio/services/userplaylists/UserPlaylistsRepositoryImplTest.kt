package com.mplayeraudio.services.userplaylists

import com.mplayeraudio.core.domain.musiclibrary.AddTrackResult
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.services.userplaylists.data.PlaylistWithTracks
import com.mplayeraudio.services.userplaylists.data.UserPlaylistDao
import com.mplayeraudio.services.userplaylists.data.UserPlaylistEntity
import com.mplayeraudio.services.userplaylists.data.UserPlaylistTrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPlaylistsRepositoryImplTest {

    private val fakeDao = FakeUserPlaylistDao()
    private val extractor = UrlMetadataExtractor()
    private val repository = UserPlaylistsRepositoryImpl(fakeDao, extractor)

    @Test
    fun `create first playlist gets default title`() = runTest {
        val ref = repository.createPlaylist()
        assertEquals(MusicProviderId.UserPlaylists, ref.provider)
        assertEquals(1L, ref.id.kind.value)
        
        val entity = fakeDao.playlists[1L]!!
        assertEquals("Новый плейлист", entity.title)
    }

    @Test
    fun `create second playlist gets suffixed title`() = runTest {
        repository.createPlaylist() // id = 1
        val ref = repository.createPlaylist() // id = 2
        
        val entity = fakeDao.playlists[2L]!!
        assertEquals("Новый плейлист 2", entity.title)
    }

    @Test
    fun `add track by valid url succeeds`() = runTest {
        val ref = repository.createPlaylist()
        val result = repository.addTrackByUrl(ref.id, "https://example.com/song.mp3")
        
        assertEquals(AddTrackResult.Success, result)
        val track = fakeDao.tracks.values.first()
        assertEquals("song.mp3", track.title)
        assertEquals("example.com", track.artist)
        assertEquals("https://example.com/song.mp3", track.url)
        assertEquals(ref.id.kind.value, track.playlistId)
    }

    @Test
    fun `add track by invalid url fails`() = runTest {
        val ref = repository.createPlaylist()
        val result = repository.addTrackByUrl(ref.id, "not a url")
        
        assertEquals(AddTrackResult.InvalidUrl, result)
        assertTrue(fakeDao.tracks.isEmpty())
    }

    private class FakeUserPlaylistDao : UserPlaylistDao {
        var nextPlaylistId = 1L
        var nextTrackId = 1L
        
        val playlists = mutableMapOf<Long, UserPlaylistEntity>()
        val tracks = mutableMapOf<Long, UserPlaylistTrackEntity>()

        override fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>> = flowOf(emptyList())

        override fun observePlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?> = flowOf(null)

        override suspend fun insertPlaylist(playlist: UserPlaylistEntity): Long {
            val id = nextPlaylistId++
            playlists[id] = playlist.copy(id = id)
            return id
        }

        override suspend fun insertTrack(track: UserPlaylistTrackEntity): Long {
            val id = nextTrackId++
            tracks[id] = track.copy(id = id)
            return id
        }

        override suspend fun deletePlaylist(playlistId: Long) {
            playlists.remove(playlistId)
            tracks.entries.removeIf { it.value.playlistId == playlistId }
        }

        override suspend fun deleteTrack(playlistId: Long, trackId: Long) {
            tracks.remove(trackId)
        }

        override suspend fun getMaxPlaylistId(): Long? {
            return playlists.keys.maxOrNull()
        }

        override suspend fun getTrackUrl(trackId: Long): String? {
            return tracks[trackId]?.url
        }
    }
}
