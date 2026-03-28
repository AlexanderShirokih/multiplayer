package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryRepository
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
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

class ObservePlaylistUseCase(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(id: PlaylistId): Flow<Playlist?> = repository.observePlaylist(id)
}

class RefreshPlaylistUseCase(
    private val repository: MusicLibraryRepository,
) {
    suspend operator fun invoke(id: PlaylistId) {
        repository.refreshPlaylist(id)
    }
}

class ObserveSavedTracksUseCase(
    private val repository: MusicLibraryRepository,
) {
    operator fun invoke(): Flow<SavedTracksResult> = repository.observeSavedTracks()
}

class RefreshSavedTracksUseCase(
    private val repository: MusicLibraryRepository,
) {
    suspend operator fun invoke() {
        repository.refreshSavedTracks()
    }
}
