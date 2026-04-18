package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.AlbumId
import com.mplayeraudio.core.domain.musiclibrary.ArtistPreview
import com.mplayeraudio.core.domain.musiclibrary.MusicLibrary
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRef
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRole
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.PlaylistTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.SavedTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.SavedTracks
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import com.mplayeraudio.core.domain.musiclibrary.TrackPreview
import com.mplayeraudio.core.domain.musiclibrary.TrackRef
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.player.PlayableSource
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
        val library = FakeMusicLibrary(
            playlist = playlistFixture(),
        )
        val playbackBridge = InMemoryPlaybackQueueBridge()
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                ref = PlaylistRef(
                    provider = MusicProviderId.YandexMusic,
                    id = playlistFixtureId,
                ),
                title = "Road Trip",
                role = PlaylistRole.Regular,
            ),
            observePlaylist = ObservePlaylistUseCase(library),
            refreshPlaylist = RefreshPlaylistUseCase(library),
            observeSavedTracks = ObserveSavedTracksUseCase(library),
            refreshSavedTracks = RefreshSavedTracksUseCase(library),
            playbackBridge = playbackBridge,
        )
        advanceUntilIdle()

        viewModel.onTrackClick(1)
        advanceUntilIdle()

        assertEquals("Road Trip", viewModel.state.value.title)
        assertEquals(2, viewModel.state.value.tracks.size)
        assertEquals("Second Track", viewModel.state.value.tracks[1].title)
        assertEquals(1, viewModel.state.value.activeTrackIndex)
        assertNull(viewModel.state.value.status)
        assertEquals(1, library.refreshPlaylistCallCount)
        assertEquals(
            PlayableSource.Remote(MusicProviderId.YandexMusic),
            viewModel.state.value.tracks.first().queueItem.source,
        )
    }

    @Test
    fun `device playlist creates remote playable source backed by device provider`() = runTest(dispatcher) {
        val library = FakeMusicLibrary(
            playlist = playlistFixture(provider = MusicProviderId.Device, trackPrefix = "device:77"),
        )
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                ref = PlaylistRef(
                    provider = MusicProviderId.Device,
                    id = playlistFixtureId,
                ),
                title = "Треки с устройства",
                role = PlaylistRole.Regular,
            ),
            observePlaylist = ObservePlaylistUseCase(library),
            refreshPlaylist = RefreshPlaylistUseCase(library),
            observeSavedTracks = ObserveSavedTracksUseCase(library),
            refreshSavedTracks = RefreshSavedTracksUseCase(library),
            playbackBridge = InMemoryPlaybackQueueBridge(),
        )
        advanceUntilIdle()

        assertEquals(
            PlayableSource.Remote(MusicProviderId.Device),
            viewModel.state.value.tracks.single().queueItem.source,
        )
    }

    @Test
    fun `favourites refresh populates saved tracks list`() = runTest(dispatcher) {
        val library = FakeMusicLibrary(
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
                ref = PlaylistRef(
                    provider = MusicProviderId.YandexMusic,
                    id = playlistFixtureId,
                ),
                title = "Любимые",
                role = PlaylistRole.Favourites,
            ),
            observePlaylist = ObservePlaylistUseCase(library),
            refreshPlaylist = RefreshPlaylistUseCase(library),
            observeSavedTracks = ObserveSavedTracksUseCase(library),
            refreshSavedTracks = RefreshSavedTracksUseCase(library),
            playbackBridge = InMemoryPlaybackQueueBridge(),
        )
        advanceUntilIdle()

        assertEquals("Любимые", viewModel.state.value.title)
        assertEquals(1, viewModel.state.value.tracks.size)
        assertEquals("Saved Track", viewModel.state.value.tracks.single().title)
        assertNull(viewModel.state.value.status)
        assertEquals(1, library.refreshSavedTracksCallCount)
    }
}

private class FakeMusicLibrary(
    private val playlist: Playlist? = null,
    private val savedTracksResult: SavedTracksResult? = null,
) : MusicLibrary {

    private val playlistFlow = MutableStateFlow<Playlist?>(null)
    private val savedTracksFlow = MutableStateFlow<SavedTracksResult?>(null)

    var refreshPlaylistCallCount = 0
    var refreshSavedTracksCallCount = 0

    override fun observeAvailability(): Flow<MusicServiceAvailability> = emptyFlow()

    override fun observeAllPlaylists(): Flow<List<PlaylistSummary>> = emptyFlow()

    override fun observePlaylist(ref: PlaylistRef): Flow<Playlist?> = playlistFlow

    override fun observeSavedTracks(): Flow<SavedTracksResult> = savedTracksFlow.filterNotNull()

    override suspend fun refreshAll() = Unit

    override suspend fun refreshPlaylist(ref: PlaylistRef) {
        refreshPlaylistCallCount += 1
        playlistFlow.value = playlist
    }

    override suspend fun refreshSavedTracks() {
        refreshSavedTracksCallCount += 1
        savedTracksFlow.value = savedTracksResult
    }
}

private fun playlistFixture(
    provider: MusicProviderId = MusicProviderId.YandexMusic,
    trackPrefix: String = "second-track",
): Playlist {
    val tracks = if (provider == MusicProviderId.Device) {
        listOf(
            playlistTrackEntry(
                position = 0,
                trackIdValue = trackPrefix,
                title = "Device Track",
                artist = "Local Artist",
            ),
        )
    } else {
        listOf(
            playlistTrackEntry(
                position = 0,
                trackIdValue = "first-track",
                title = "First Track",
                artist = "Artist One",
            ),
            playlistTrackEntry(
                position = 1,
                trackIdValue = "second-track",
                title = "Second Track",
                artist = "Artist Two",
            ),
        )
    }

    return Playlist(
        summary = PlaylistSummary(
            id = playlistFixtureId,
            provider = provider,
            playlistUuid = null,
            title = "Road Trip",
            ownerName = null,
            coverUriTemplate = null,
            trackCount = tracks.size,
            durationMs = 420_000L,
            isAvailable = true,
            isCollective = false,
            visibility = null,
            role = PlaylistRole.Regular,
        ),
        revision = 1L,
        snapshot = 1L,
        likesCount = null,
        tracks = tracks,
    )
}

private fun playlistTrackEntry(
    position: Int,
    trackIdValue: String,
    title: String,
    artist: String,
): PlaylistTrackEntry {
    return PlaylistTrackEntry(
        position = position,
        addedAt = null,
        originalIndex = null,
        originalShuffleIndex = null,
        isRecent = null,
        trackRef = trackRef(trackIdValue),
        track = com.mplayeraudio.core.domain.musiclibrary.Track(
            preview = trackPreview(
                title = title,
                artist = artist,
                trackIdValue = trackIdValue,
            ),
            lyricsAvailable = false,
            isAvailableForPremium = true,
            isAvailableWithoutPermission = false,
        ),
    )
}

private fun trackPreview(
    title: String,
    artist: String,
    trackIdValue: String = title.lowercase().replace(' ', '-'),
): TrackPreview {
    return TrackPreview(
        ref = trackRef(trackIdValue),
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

private val playlistFixtureId = PlaylistId(
    ownerId = ProviderUserId("owner"),
    kind = PlaylistKind(42L),
)
