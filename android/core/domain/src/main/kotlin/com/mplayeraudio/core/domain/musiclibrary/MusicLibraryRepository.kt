package com.mplayeraudio.core.domain.musiclibrary

import kotlinx.coroutines.flow.Flow

interface MusicLibraryRepository {
    fun observeAvailability(): Flow<MusicServiceAvailability>

    fun observeOwnPlaylists(): Flow<List<PlaylistSummary>>

    fun observePlaylist(id: PlaylistId): Flow<Playlist?>

    fun observeSavedTracks(): Flow<SavedTracksResult>

    fun observeTracks(refs: List<TrackRef>): Flow<List<Track>>

    suspend fun refreshAvailability()

    suspend fun refreshOwnPlaylists()

    suspend fun refreshPlaylist(id: PlaylistId)

    suspend fun refreshSavedTracks()

    suspend fun refreshTracks(refs: List<TrackRef>)
}
