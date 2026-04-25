package com.mplayeraudio.services.userplaylists

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.UserPlaylistTrackId
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.services.userplaylists.data.PlaylistWithTracks
import com.mplayeraudio.services.userplaylists.data.UserPlaylistDao
import com.mplayeraudio.services.userplaylists.data.UserPlaylistEntity
import com.mplayeraudio.services.userplaylists.data.UserPlaylistTrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UserPlaylistsTrackStreamUrlProviderTest {

    @Test
    fun `get stream url resolves numeric local track id`() = runTest {
        val dao = FakeUserPlaylistDao(
            urlsByTrackId = mapOf(
                1L to "http://192.168.3.11:3444/stream/e3e3a15a-19e2-4397-ab36-e8e0dd557433.m3u8",
            ),
        )
        val provider = UserPlaylistsTrackStreamUrlProvider(dao)

        val url = provider.getStreamUrl(UserPlaylistTrackId(1L))

        assertEquals(
            "http://192.168.3.11:3444/stream/e3e3a15a-19e2-4397-ab36-e8e0dd557433.m3u8",
            url,
        )
    }

    @Test
    fun `non user playlist track id is reported as provider error`() = runTest {
        val provider = UserPlaylistsTrackStreamUrlProvider(FakeUserPlaylistDao())

        val error = try {
            provider.getStreamUrl(YandexTrackId("e3e3a15a-19e2-4397-ab36-e8e0dd557433"))
            null
        } catch (error: MusicLibraryException.ProviderError) {
            error
        }

        assertNotNull(error)
        assertEquals("invalid-track-id", error?.code)
    }
}

private class FakeUserPlaylistDao(
    private val urlsByTrackId: Map<Long, String?> = emptyMap(),
) : UserPlaylistDao {

    override fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>> = flowOf(emptyList())

    override fun observePlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?> = flowOf(null)

    override suspend fun insertPlaylist(playlist: UserPlaylistEntity): Long = playlist.id

    override suspend fun insertTrack(track: UserPlaylistTrackEntity): Long = track.id

    override suspend fun deletePlaylist(playlistId: Long) = Unit

    override suspend fun deleteTrack(playlistId: Long, trackId: Long) = Unit

    override suspend fun getMaxPlaylistId(): Long? = null

    override suspend fun getTrackUrl(trackId: Long): String? = urlsByTrackId[trackId]
}
