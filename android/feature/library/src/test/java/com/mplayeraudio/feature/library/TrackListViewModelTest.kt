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
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.core.domain.musiclibrary.AddTrackResult
import com.mplayeraudio.core.player.NowPlayingStripExternalState
import com.mplayeraudio.core.player.PlaybackError
import com.mplayeraudio.core.player.PlaybackPhase
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import com.mplayeraudio.core.player.PlayableSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(FakeUserPlaylistsRepository()),
            deletePlaylist = DeleteUserPlaylistUseCase(FakeUserPlaylistsRepository()),
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
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(FakeUserPlaylistsRepository()),
            deletePlaylist = DeleteUserPlaylistUseCase(FakeUserPlaylistsRepository()),
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
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(FakeUserPlaylistsRepository()),
            deletePlaylist = DeleteUserPlaylistUseCase(FakeUserPlaylistsRepository()),
        )
        advanceUntilIdle()

        assertEquals("Любимые", viewModel.state.value.title)
        assertEquals(1, viewModel.state.value.tracks.size)
        assertEquals("Saved Track", viewModel.state.value.tracks.single().title)
        assertNull(viewModel.state.value.status)
        assertEquals(1, library.refreshSavedTracksCallCount)
    }

    @Test
    fun `active track follows current queue item id instead of raw playback index`() = runTest(dispatcher) {
        val playlist = playlistFixture()
        val playbackBridge = ManualPlaybackQueueBridge()
        val library = FakeMusicLibrary(playlist = playlist)
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
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(FakeUserPlaylistsRepository()),
            deletePlaylist = DeleteUserPlaylistUseCase(FakeUserPlaylistsRepository()),
        )
        advanceUntilIdle()

        val displayedTracks = viewModel.state.value.tracks
        playbackBridge.emitState(
            PlaybackQueueState(
                queue = listOf(
                    displayedTracks[1].queueItem,
                    displayedTracks[0].queueItem,
                ),
                currentIndex = 0,
                phase = PlaybackPhase.Playing,
                currentPositionMs = 15_000L,
            ),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.activeTrackIndex)
        assertEquals(1, viewModel.state.value.playingTrackIndex)
        assertEquals("Second Track", viewModel.state.value.tracks[1].title)
    }

    @Test
    fun `playback error keeps current track selected but stops playing indicator`() = runTest(dispatcher) {
        val playlist = playlistFixture()
        val playbackBridge = ManualPlaybackQueueBridge()
        val library = FakeMusicLibrary(playlist = playlist)
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
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(FakeUserPlaylistsRepository()),
            deletePlaylist = DeleteUserPlaylistUseCase(FakeUserPlaylistsRepository()),
        )
        advanceUntilIdle()

        val displayedTracks = viewModel.state.value.tracks
        playbackBridge.emitState(
            PlaybackQueueState(
                queue = displayedTracks.map(TrackListItemState::queueItem),
                currentIndex = 1,
                phase = PlaybackPhase.Failed,
                playbackError = PlaybackError.TrackUnavailable(
                    itemId = displayedTracks[1].queueItem.id,
                    message = "Network is unreachable",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.activeTrackIndex)
        assertNull(viewModel.state.value.playingTrackIndex)
        assertNotNull(viewModel.state.value.playbackError)
        assertTrue(viewModel.state.value.playbackError is PlaybackError.TrackUnavailable)
    }

    @Test
    fun `same playlist emission does not replace queue again`() = runTest(dispatcher) {
        val playlist = playlistFixture()
        val playbackBridge = CountingPlaybackQueueBridge()
        val library = FakeMusicLibrary(playlist = playlist)
        TrackListViewModel(
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
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(FakeUserPlaylistsRepository()),
            deletePlaylist = DeleteUserPlaylistUseCase(FakeUserPlaylistsRepository()),
        )
        advanceUntilIdle()

        library.emitPlaylist(playlist)
        advanceUntilIdle()

        assertEquals(1, playbackBridge.replaceQueueCallCount)
    }

    @Test
    fun `onToggleEditing toggles isEditing state`() = runTest(dispatcher) {
        val library = FakeMusicLibrary(playlist = playlistFixture())
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                ref = PlaylistRef(
                    provider = MusicProviderId.UserPlaylists,
                    id = playlistFixtureId,
                ),
                title = "My Playlist",
                role = PlaylistRole.Regular,
            ),
            observePlaylist = ObservePlaylistUseCase(library),
            refreshPlaylist = RefreshPlaylistUseCase(library),
            observeSavedTracks = ObserveSavedTracksUseCase(library),
            refreshSavedTracks = RefreshSavedTracksUseCase(library),
            playbackBridge = InMemoryPlaybackQueueBridge(),
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(FakeUserPlaylistsRepository()),
            deletePlaylist = DeleteUserPlaylistUseCase(FakeUserPlaylistsRepository()),
        )
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isEditing)
        assertEquals(true, viewModel.state.value.canEdit)

        viewModel.onToggleEditing()
        advanceUntilIdle()
        assertEquals(true, viewModel.state.value.isEditing)

        viewModel.onToggleEditing()
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.isEditing)
    }

    @Test
    fun `onAddTrackByUrl success refreshes playlist`() = runTest(dispatcher) {
        val library = FakeMusicLibrary(playlist = playlistFixture())
        val repo = FakeUserPlaylistsRepository()
        repo.addTrackResult = com.mplayeraudio.core.domain.musiclibrary.AddTrackResult.Success
        
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                ref = PlaylistRef(
                    provider = MusicProviderId.UserPlaylists,
                    id = playlistFixtureId,
                ),
                title = "My Playlist",
                role = PlaylistRole.Regular,
            ),
            observePlaylist = ObservePlaylistUseCase(library),
            refreshPlaylist = RefreshPlaylistUseCase(library),
            observeSavedTracks = ObserveSavedTracksUseCase(library),
            refreshSavedTracks = RefreshSavedTracksUseCase(library),
            playbackBridge = InMemoryPlaybackQueueBridge(),
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(repo),
            deletePlaylist = DeleteUserPlaylistUseCase(repo),
        )
        advanceUntilIdle()
        
        val initialCallCount = library.refreshPlaylistCallCount
        
        viewModel.onAddTrackByUrl("https://example.com/song.mp3")
        advanceUntilIdle()
        
        assertEquals(initialCallCount + 1, library.refreshPlaylistCallCount)
        assertNull(viewModel.state.value.addTrackError)
    }

    @Test
    fun `onAddTrackByUrl invalid url sets error state`() = runTest(dispatcher) {
        val library = FakeMusicLibrary(playlist = playlistFixture())
        val repo = FakeUserPlaylistsRepository()
        repo.addTrackResult = com.mplayeraudio.core.domain.musiclibrary.AddTrackResult.InvalidUrl
        
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                ref = PlaylistRef(
                    provider = MusicProviderId.UserPlaylists,
                    id = playlistFixtureId,
                ),
                title = "My Playlist",
                role = PlaylistRole.Regular,
            ),
            observePlaylist = ObservePlaylistUseCase(library),
            refreshPlaylist = RefreshPlaylistUseCase(library),
            observeSavedTracks = ObserveSavedTracksUseCase(library),
            refreshSavedTracks = RefreshSavedTracksUseCase(library),
            playbackBridge = InMemoryPlaybackQueueBridge(),
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(repo),
            deletePlaylist = DeleteUserPlaylistUseCase(repo),
        )
        advanceUntilIdle()
        
        viewModel.onAddTrackByUrl("invalid-url")
        advanceUntilIdle()
        
        assertEquals("InvalidUrl", viewModel.state.value.addTrackError)
    }

    @Test
    fun `onDeletePlaylist marks playlist as deleted and clears playback queue`() = runTest(dispatcher) {
        val library = FakeMusicLibrary(playlist = playlistFixture())
        val repo = FakeUserPlaylistsRepository()
        val playbackBridge = CountingPlaybackQueueBridge()
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                ref = PlaylistRef(
                    provider = MusicProviderId.UserPlaylists,
                    id = playlistFixtureId,
                ),
                title = "My Playlist",
                role = PlaylistRole.Regular,
            ),
            observePlaylist = ObservePlaylistUseCase(library),
            refreshPlaylist = RefreshPlaylistUseCase(library),
            observeSavedTracks = ObserveSavedTracksUseCase(library),
            refreshSavedTracks = RefreshSavedTracksUseCase(library),
            playbackBridge = playbackBridge,
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(repo),
            deletePlaylist = DeleteUserPlaylistUseCase(repo),
        )
        advanceUntilIdle()

        val initialReplaceCount = playbackBridge.replaceQueueCallCount

        viewModel.onDeletePlaylist()
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.playlistDeleted)
        assertEquals(false, viewModel.state.value.isDeletingPlaylist)
        assertEquals(false, viewModel.state.value.isEditing)
        assertEquals(listOf(playlistFixtureId), repo.deletedPlaylistIds)
        assertEquals(initialReplaceCount + 1, playbackBridge.replaceQueueCallCount)
    }

    @Test
    fun `onDeletePlaylist surfaces error message when repository fails`() = runTest(dispatcher) {
        val library = FakeMusicLibrary(playlist = playlistFixture())
        val repo = FakeUserPlaylistsRepository().apply {
            deletePlaylistError = IllegalStateException("network down")
        }
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                ref = PlaylistRef(
                    provider = MusicProviderId.UserPlaylists,
                    id = playlistFixtureId,
                ),
                title = "My Playlist",
                role = PlaylistRole.Regular,
            ),
            observePlaylist = ObservePlaylistUseCase(library),
            refreshPlaylist = RefreshPlaylistUseCase(library),
            observeSavedTracks = ObserveSavedTracksUseCase(library),
            refreshSavedTracks = RefreshSavedTracksUseCase(library),
            playbackBridge = InMemoryPlaybackQueueBridge(),
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(repo),
            deletePlaylist = DeleteUserPlaylistUseCase(repo),
        )
        advanceUntilIdle()

        viewModel.onDeletePlaylist()
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.playlistDeleted)
        assertEquals(false, viewModel.state.value.isDeletingPlaylist)
        assertEquals("network down", viewModel.state.value.playlistDeleteErrorMessage)
    }

    @Test
    fun `onDeletePlaylist is no-op when playlist is not editable`() = runTest(dispatcher) {
        val library = FakeMusicLibrary(playlist = playlistFixture())
        val repo = FakeUserPlaylistsRepository()
        val viewModel = TrackListViewModel(
            destination = LibraryTrackListDestination(
                ref = PlaylistRef(
                    provider = MusicProviderId.YandexMusic,
                    id = playlistFixtureId,
                ),
                title = "Read-only",
                role = PlaylistRole.Regular,
            ),
            observePlaylist = ObservePlaylistUseCase(library),
            refreshPlaylist = RefreshPlaylistUseCase(library),
            observeSavedTracks = ObserveSavedTracksUseCase(library),
            refreshSavedTracks = RefreshSavedTracksUseCase(library),
            playbackBridge = InMemoryPlaybackQueueBridge(),
            addTrackToPlaylist = AddUserPlaylistTrackUseCase(repo),
            deletePlaylist = DeleteUserPlaylistUseCase(repo),
        )
        advanceUntilIdle()

        viewModel.onDeletePlaylist()
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.playlistDeleted)
        assertTrue(repo.deletedPlaylistIds.isEmpty())
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

    fun emitPlaylist(value: Playlist?) {
        playlistFlow.value = value
    }
}

private class ManualPlaybackQueueBridge(
    initialState: PlaybackQueueState = PlaybackQueueState(),
) : PlaybackQueueBridge {

    private val stateFlow = MutableStateFlow(initialState)

    override val playbackState: StateFlow<PlaybackQueueState> = stateFlow.asStateFlow()

    override val state: Flow<NowPlayingStripExternalState> = playbackState.map { ps ->
        val currentItem = ps.currentItem
        NowPlayingStripExternalState(
            title = currentItem?.title.orEmpty(),
            subtitle = currentItem?.subtitle.orEmpty(),
            isPlaying = ps.isPlaying,
            currentPositionMs = ps.currentPositionMs,
            durationMs = ps.currentDurationMs ?: currentItem?.durationMs ?: 0L,
            controlsEnabled = ps.controlsEnabled,
        )
    }

    fun emitState(state: PlaybackQueueState) { stateFlow.value = state }

    override suspend fun replaceQueue(queue: List<PlaybackQueueItem>, startIndex: Int?, autoPlay: Boolean) = Unit
    override suspend fun playTrack(index: Int) = Unit
    override suspend fun play() = Unit
    override suspend fun pause() = Unit
    override suspend fun skipNext() = Unit
    override suspend fun skipPrevious() = Unit
    override suspend fun seekTo(positionMs: Long) = Unit
    override fun acknowledgeError() = Unit
    override fun shutdown() = Unit
}

private class CountingPlaybackQueueBridge : PlaybackQueueBridge {

    private val stateFlow = MutableStateFlow(PlaybackQueueState())

    var replaceQueueCallCount = 0
        private set

    override val playbackState: StateFlow<PlaybackQueueState> = stateFlow.asStateFlow()

    override val state: Flow<NowPlayingStripExternalState> = playbackState.map { ps ->
        val currentItem = ps.currentItem
        NowPlayingStripExternalState(
            title = currentItem?.title.orEmpty(),
            subtitle = currentItem?.subtitle.orEmpty(),
            isPlaying = ps.isPlaying,
            currentPositionMs = ps.currentPositionMs,
            durationMs = ps.currentDurationMs ?: currentItem?.durationMs ?: 0L,
            controlsEnabled = ps.controlsEnabled,
        )
    }

    override suspend fun replaceQueue(queue: List<PlaybackQueueItem>, startIndex: Int?, autoPlay: Boolean) {
        replaceQueueCallCount += 1
        stateFlow.value = PlaybackQueueState(
            queue = queue,
            currentIndex = startIndex,
            phase = if (autoPlay && queue.isNotEmpty()) PlaybackPhase.Playing else PlaybackPhase.Idle,
        )
    }

    override suspend fun playTrack(index: Int) = Unit
    override suspend fun play() = Unit
    override suspend fun pause() = Unit
    override suspend fun skipNext() = Unit
    override suspend fun skipPrevious() = Unit
    override suspend fun seekTo(positionMs: Long) = Unit
    override fun acknowledgeError() = Unit
    override fun shutdown() = Unit
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
        trackId = YandexTrackId(trackIdValue),
        albumId = AlbumId("album-$trackIdValue"),
    )
}

private val playlistFixtureId = PlaylistId(
    ownerId = ProviderUserId("owner"),
    kind = PlaylistKind(42L),
)

private class FakeUserPlaylistsRepository :
    com.mplayeraudio.core.domain.musiclibrary.UserPlaylistsRepository {
    var addTrackResult: AddTrackResult = AddTrackResult.Success
    var deletePlaylistError: Exception? = null
    var deletedPlaylistIds: MutableList<PlaylistId> = mutableListOf()

    override suspend fun createPlaylist(): PlaylistRef = PlaylistRef(
        MusicProviderId.UserPlaylists,
        playlistFixtureId,
    )

    override suspend fun addTrackByUrl(
        playlistId: PlaylistId,
        url: String,
    ): AddTrackResult = addTrackResult

    override suspend fun deleteTrack(playlistId: PlaylistId, trackId: TrackId) = Unit
    override suspend fun deletePlaylist(playlistId: PlaylistId) {
        deletePlaylistError?.let { throw it }
        deletedPlaylistIds.add(playlistId)
    }
}
