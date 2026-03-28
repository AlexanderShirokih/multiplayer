package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryRepository
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import kotlinx.coroutines.flow.Flow

class ObserveOwnPlaylistsUseCase(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(): Flow<List<PlaylistSummary>> = repository.observeOwnPlaylists()
}

class RefreshLibraryUseCase(
    private val repository: MusicLibraryRepository,
) {
    suspend operator fun invoke() {
        repository.refreshOwnPlaylists()
    }
}
