package com.mplayeraudio.core.data

import com.mplayeraudio.core.domain.musiclibrary.MusicProvider
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRef
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRole
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMusicLibraryTest {

    @Test
    fun `observeAllPlaylists combines providers in stable order`() = runTest {
        val device = FakeMusicProvider(
            id = MusicProviderId.Device,
            playlists = listOf(playlistSummary(provider = MusicProviderId.Device, title = "Device")),
        )
        val yandex = FakeMusicProvider(
            id = MusicProviderId.YandexMusic,
            playlists = listOf(playlistSummary(provider = MusicProviderId.YandexMusic, title = "Yandex")),
        )

        val library = DefaultMusicLibrary(setOf(yandex, device))
        val playlists = library.observeAllPlaylists().first()

        assertEquals(listOf(MusicProviderId.Device, MusicProviderId.YandexMusic), playlists.map { it.provider })
    }

    @Test
    fun `observePlaylist dispatches by provider`() = runTest {
        val devicePlaylist = Playlist(
            summary = playlistSummary(provider = MusicProviderId.Device, title = "Device"),
            revision = null,
            snapshot = null,
            likesCount = null,
            tracks = emptyList(),
        )
        val library = DefaultMusicLibrary(
            setOf(
                FakeMusicProvider(id = MusicProviderId.Device, playlist = devicePlaylist),
                FakeMusicProvider(id = MusicProviderId.YandexMusic),
            ),
        )

        val playlist = library.observePlaylist(
            PlaylistRef(
                provider = MusicProviderId.Device,
                id = devicePlaylist.summary.id,
            ),
        ).first()

        assertEquals("Device", playlist?.summary?.title)
    }

    @Test
    fun `refreshAll invokes every provider`() = runTest {
        val first = FakeMusicProvider(id = MusicProviderId.Device)
        val second = FakeMusicProvider(id = MusicProviderId.YandexMusic)
        val library = DefaultMusicLibrary(setOf(first, second))

        library.refreshAll()

        assertEquals(1, first.refreshAvailabilityCalls)
        assertEquals(1, first.refreshPlaylistsCalls)
        assertEquals(1, second.refreshAvailabilityCalls)
        assertEquals(1, second.refreshPlaylistsCalls)
    }

    @Test
    fun `observeAvailability is available when any provider is available`() = runTest {
        val library = DefaultMusicLibrary(
            setOf(
                FakeMusicProvider(
                    id = MusicProviderId.Device,
                    availability = MusicServiceAvailability(
                        isAvailable = false,
                        region = null,
                        permissions = emptySet(),
                    ),
                ),
                FakeMusicProvider(
                    id = MusicProviderId.YandexMusic,
                    availability = MusicServiceAvailability(
                        isAvailable = true,
                        region = 225,
                        permissions = setOf("streaming"),
                    ),
                ),
            ),
        )

        val availability = library.observeAvailability().first()

        assertTrue(availability.isAvailable)
        assertEquals(225, availability.region)
        assertEquals(setOf("streaming"), availability.permissions)
    }
}

private class FakeMusicProvider(
    override val id: MusicProviderId,
    playlists: List<PlaylistSummary> = emptyList(),
    playlist: Playlist? = null,
    private val availability: MusicServiceAvailability = MusicServiceAvailability(
        isAvailable = true,
        region = null,
        permissions = emptySet(),
    ),
) : MusicProvider {
    private val playlistsFlow = MutableStateFlow(playlists)
    private val playlistFlow = MutableStateFlow(playlist)

    var refreshAvailabilityCalls = 0
    var refreshPlaylistsCalls = 0

    override fun observeAvailability(): Flow<MusicServiceAvailability> = flowOf(availability)

    override fun observePlaylists(): Flow<List<PlaylistSummary>> = playlistsFlow

    override fun observePlaylist(id: PlaylistId): Flow<Playlist?> = playlistFlow

    override fun observeSavedTracks(): Flow<SavedTracksResult> = flowOf(SavedTracksResult.PrivateLibrary)

    override suspend fun refreshAvailability() {
        refreshAvailabilityCalls += 1
    }

    override suspend fun refreshPlaylists() {
        refreshPlaylistsCalls += 1
    }

    override suspend fun refreshPlaylist(id: PlaylistId) = Unit

    override suspend fun refreshSavedTracks() = Unit
}

private fun playlistSummary(
    provider: MusicProviderId,
    title: String,
): PlaylistSummary {
    return PlaylistSummary(
        id = PlaylistId(
            ownerId = ProviderUserId(provider.name),
            kind = PlaylistKind(1L),
        ),
        provider = provider,
        playlistUuid = null,
        title = title,
        ownerName = null,
        coverUriTemplate = null,
        trackCount = 0,
        durationMs = null,
        isAvailable = true,
        isCollective = false,
        visibility = null,
        role = PlaylistRole.Regular,
    )
}
