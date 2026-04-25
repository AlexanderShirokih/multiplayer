package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.MusicLibrary
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRef
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.UserPlaylistsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicLibraryViewModelTest {

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
    fun `onCreatePlaylistClick creates playlist and emits NavigateToPlaylistEditor effect`() = runTest {
        val library = FakeMusicLibrary()
        val repo = FakeUserPlaylistsRepository()
        
        val viewModel = MusicLibraryViewModel(
            observeOwnPlaylists = ObserveOwnPlaylistsUseCase(library),
            refreshLibrary = RefreshLibraryUseCase(library),
            createPlaylist = CreateUserPlaylistUseCase(repo),
        )
        
        advanceUntilIdle()
        
        val effects = mutableListOf<MusicLibraryEffect>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects.add(it) }
        }
        
        viewModel.onCreatePlaylistClick()
        advanceUntilIdle()
        
        assertEquals(1, effects.size)
        val effect = effects.first()
        assertTrue(effect is MusicLibraryEffect.NavigateToPlaylistEditor)
        val navEffect = effect as MusicLibraryEffect.NavigateToPlaylistEditor
        assertEquals(MusicProviderId.UserPlaylists, navEffect.destination.ref.provider)
        assertTrue(navEffect.destination.initiallyEditing)
        
        job.cancel()
    }

    private class FakeMusicLibrary : MusicLibrary {
        override fun observeAvailability(): Flow<MusicServiceAvailability> = emptyFlow()
        override fun observeAllPlaylists(): Flow<List<PlaylistSummary>> = flowOf(emptyList())
        override fun observePlaylist(ref: PlaylistRef): Flow<Playlist?> = emptyFlow()
        override fun observeSavedTracks(): Flow<SavedTracksResult> = emptyFlow()
        override suspend fun refreshAll() = Unit
        override suspend fun refreshPlaylist(ref: PlaylistRef) = Unit
        override suspend fun refreshSavedTracks() = Unit
    }

    private class FakeUserPlaylistsRepository : UserPlaylistsRepository {
        override suspend fun createPlaylist(): PlaylistRef = PlaylistRef(
            provider = MusicProviderId.UserPlaylists,
            id = PlaylistId(ProviderUserId("owner"), PlaylistKind(1L))
        )
        override suspend fun addTrackByUrl(
            playlistId: PlaylistId,
            url: String,
        ) = com.mplayeraudio.core.domain.musiclibrary.AddTrackResult.Success
        
        override suspend fun deleteTrack(playlistId: PlaylistId, trackId: TrackId) = Unit
        override suspend fun deletePlaylist(playlistId: PlaylistId) = Unit
    }
}
