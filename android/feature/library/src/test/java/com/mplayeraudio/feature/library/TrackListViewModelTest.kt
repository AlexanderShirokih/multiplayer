package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.AlbumId
import com.mplayeraudio.core.domain.musiclibrary.ArtistPreview
import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryRepository
import com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRole
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.PlaylistTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.SavedTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.SavedTracks
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import com.mplayeraudio.core.domain.musiclibrary.Track
import com.mplayeraudio.core.domain.musiclibrary.TrackPreview
import com.mplayeraudio.core.domain.musiclibrary.TrackRef
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackListViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `regular playlist refresh populates track list and activates clicked track`() = runTest(dispatcher) {
        val repository = FakeMusicLibraryRepository(
            playlist = playlistFixture(),
        )
        val playbackBridge = InMemoryPlaybackQueueBridge()
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                playlistId = playlistFixtureId,
                title = "Road Trip",
                role = PlaylistRole.Regular,
            ),
            observePlaylist = ObservePlaylistUseCase(repository),
            refreshPlaylist = RefreshPlaylistUseCase(repository),
            observeSavedTracks = ObserveSavedTracksUseCase(repository),
            refreshSavedTracks = RefreshSavedTracksUseCase(repository),
            playbackBridge = playbackBridge,
        )
        advanceUntilIdle()

        viewModel.onTrackClick(1)
        advanceUntilIdle()

        assertEquals("Road Trip", viewModel.state.value.title)
        assertEquals(2, viewModel.state.value.trackCount)
        assertEquals(2, viewModel.state.value.tracks.size)
        assertEquals("Second Track", viewModel.state.value.tracks[1].title)
        assertEquals(1, viewModel.state.value.activeTrackIndex)
        assertNull(viewModel.state.value.status)
        assertEquals(1, repository.refreshPlaylistCallCount)
    }

    @Test
    fun `favourites refresh populates saved tracks list`() = runTest(dispatcher) {
        val repository = FakeMusicLibraryRepository(
            savedTracksResult = SavedTracksResult.Available(
                SavedTracks(
                    ownerId = ProviderUserId("owner"),
                    revision = 5L,
                    tracks = listOf(
                        SavedTrackEntry(
                            position = 0,
                            addedAt = null,
                            trackRef = trackRef("saved-track"),
                            track = trackPreview(
                                title = "Saved Track",
                                artist = "Saved Artist",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                playlistId = playlistFixtureId,
                title = "Любимые",
                role = PlaylistRole.Favourites,
            ),
            observePlaylist = ObservePlaylistUseCase(repository),
            refreshPlaylist = RefreshPlaylistUseCase(repository),
            observeSavedTracks = ObserveSavedTracksUseCase(repository),
            refreshSavedTracks = RefreshSavedTracksUseCase(repository),
            playbackBridge = InMemoryPlaybackQueueBridge(),
        )
        advanceUntilIdle()

        assertEquals("Любимые", viewModel.state.value.title)
        assertEquals(1, viewModel.state.value.trackCount)
        assertEquals("Saved Track", viewModel.state.value.tracks.single().title)
        assertNull(viewModel.state.value.status)
        assertEquals(1, repository.refreshSavedTracksCallCount)
    }
}

private class FakeMusicLibraryRepository(
    private val playlist: Playlist? = null,
    private val savedTracksResult: SavedTracksResult? = null,
) : MusicLibraryRepository {

    private val playlistFlow = MutableStateFlow<Playlist?>(null)
    private val savedTracksFlow = MutableStateFlow<SavedTracksResult?>(null)

    var refreshPlaylistCallCount = 0
    var refreshSavedTracksCallCount = 0

    override fun observeAvailability(): Flow<MusicServiceAvailability> = emptyFlow()

    override fun observeOwnPlaylists() = emptyFlow<List<PlaylistSummary>>()

    override fun observePlaylist(id: PlaylistId): Flow<Playlist?> = playlistFlow

    override fun observeSavedTracks(): Flow<SavedTracksResult> = savedTracksFlow.filterNotNullValue()

    override fun observeTracks(refs: List<TrackRef>): Flow<List<Track>> = emptyFlow()

    override suspend fun refreshAvailability() = Unit

    override suspend fun refreshOwnPlaylists() = Unit

    override suspend fun refreshPlaylist(id: PlaylistId) {
        refreshPlaylistCallCount += 1
        playlistFlow.value = playlist
    }

    override suspend fun refreshSavedTracks() {
        refreshSavedTracksCallCount += 1
        savedTracksFlow.value = savedTracksResult
    }

    override suspend fun refreshTracks(refs: List<TrackRef>) = Unit
}

private fun playlistFixture(): Playlist {
    return Playlist(
        summary = PlaylistSummary(
            id = playlistFixtureId,
            provider = com.mplayeraudio.core.domain.musiclibrary.MusicProviderId.YandexMusic,
            playlistUuid = null,
            title = "Road Trip",
            ownerName = null,
            coverUriTemplate = null,
            trackCount = 2,
            durationMs = 420_000L,
            isAvailable = true,
            isCollective = false,
            visibility = null,
            role = PlaylistRole.Regular,
        ),
        revision = 1L,
        snapshot = 1L,
        likesCount = null,
        tracks = listOf(
            PlaylistTrackEntry(
                position = 0,
                addedAt = null,
                originalIndex = null,
                originalShuffleIndex = null,
                isRecent = null,
                trackRef = trackRef("first-track"),
                track = Track(
                    preview = trackPreview(
                        title = "First Track",
                        artist = "Artist One",
                    ),
                    lyricsAvailable = false,
                    isAvailableForPremium = true,
                    isAvailableWithoutPermission = false,
                ),
            ),
            PlaylistTrackEntry(
                position = 1,
                addedAt = null,
                originalIndex = null,
                originalShuffleIndex = null,
                isRecent = null,
                trackRef = trackRef("second-track"),
                track = Track(
                    preview = trackPreview(
                        title = "Second Track",
                        artist = "Artist Two",
                    ),
                    lyricsAvailable = false,
                    isAvailableForPremium = true,
                    isAvailableWithoutPermission = false,
                ),
            ),
        ),
    )
}

private fun trackPreview(
    title: String,
    artist: String,
): TrackPreview {
    return TrackPreview(
        ref = trackRef(title.lowercase().replace(' ', '-')),
        title = title,
        artists = listOf(ArtistPreview(id = artist, name = artist)),
        durationMs = 180_000L,
        coverUriTemplate = null,
        isAvailable = true,
    )
}

private fun trackRef(trackIdValue: String): TrackRef {
    return TrackRef(
        trackId = TrackId(trackIdValue),
        albumId = AlbumId("album-$trackIdValue"),
    )
}

private fun <T> MutableStateFlow<T?>.filterNotNullValue(): Flow<T> {
    return filterNotNull()
}

private val playlistFixtureId = PlaylistId(
    ownerId = ProviderUserId("owner"),
    kind = PlaylistKind(42L),
)
