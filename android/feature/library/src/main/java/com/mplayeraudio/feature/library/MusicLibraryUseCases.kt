package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryRepository
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import kotlinx.coroutines.flow.Flow

class ObserveOwnPlaylistsUseCase(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(): Flow<List<PlaylistSummary>> = repository.observeOwnPlaylists()
}

class ObserveSavedTracksUseCase(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(): Flow<SavedTracksResult> = repository.observeSavedTracks()
}

class RefreshLibraryUseCase(
    private val repository: MusicLibraryRepository,
) {
    suspend operator fun invoke() {
        repository.refreshOwnPlaylists()
        repository.refreshSavedTracks()
    }
}
