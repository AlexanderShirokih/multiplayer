package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.MusicLibrary
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRef
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import kotlinx.coroutines.flow.Flow

class ObserveOwnPlaylistsUseCase(
    private val library: MusicLibrary,
) {
    operator fun invoke(): Flow<List<PlaylistSummary>> = library.observeAllPlaylists()
}

class RefreshLibraryUseCase(
    private val library: MusicLibrary,
) {
    suspend operator fun invoke() {
        library.refreshAll()
    }
}

class ObservePlaylistUseCase(
    private val library: MusicLibrary,
) {
    operator fun invoke(ref: PlaylistRef): Flow<Playlist?> = library.observePlaylist(ref)
}

class RefreshPlaylistUseCase(
    private val library: MusicLibrary,
) {
    suspend operator fun invoke(ref: PlaylistRef) {
        library.refreshPlaylist(ref)
    }
}

class ObserveSavedTracksUseCase(
    private val library: MusicLibrary,
) {
    operator fun invoke(): Flow<SavedTracksResult> = library.observeSavedTracks()
}

class RefreshSavedTracksUseCase(
    private val library: MusicLibrary,
) {
    suspend operator fun invoke() {
        library.refreshSavedTracks()
    }
}

class CreateUserPlaylistUseCase(
    private val repository: com.mplayeraudio.core.domain.musiclibrary.UserPlaylistsRepository,
) {
    suspend operator fun invoke(): PlaylistRef = repository.createPlaylist()
}

class AddUserPlaylistTrackUseCase(
    private val repository: com.mplayeraudio.core.domain.musiclibrary.UserPlaylistsRepository,
) {
    suspend operator fun invoke(
        playlistId: com.mplayeraudio.core.domain.musiclibrary.PlaylistId,
        url: String,
    ): com.mplayeraudio.core.domain.musiclibrary.AddTrackResult = repository.addTrackByUrl(playlistId, url)
}

class DeleteUserPlaylistUseCase(
    private val repository: com.mplayeraudio.core.domain.musiclibrary.UserPlaylistsRepository,
) {
    suspend operator fun invoke(
        playlistId: com.mplayeraudio.core.domain.musiclibrary.PlaylistId,
    ) {
        repository.deletePlaylist(playlistId)
    }
}
