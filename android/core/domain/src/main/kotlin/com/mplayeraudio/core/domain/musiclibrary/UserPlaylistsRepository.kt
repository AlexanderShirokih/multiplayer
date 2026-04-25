package com.mplayeraudio.core.domain.musiclibrary

interface UserPlaylistsRepository {
    suspend fun createPlaylist(): PlaylistRef
    suspend fun addTrackByUrl(playlistId: PlaylistId, url: String): AddTrackResult
    suspend fun deleteTrack(playlistId: PlaylistId, trackId: TrackId)
    suspend fun deletePlaylist(playlistId: PlaylistId)
}

sealed interface AddTrackResult {
    data object Success : AddTrackResult
    data object InvalidUrl : AddTrackResult
}
