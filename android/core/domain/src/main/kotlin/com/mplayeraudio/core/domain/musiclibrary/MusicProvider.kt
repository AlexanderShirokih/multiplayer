package com.mplayeraudio.core.domain.musiclibrary

import kotlinx.coroutines.flow.Flow

interface MusicProvider {
    val id: MusicProviderId

    fun observeAvailability(): Flow<MusicServiceAvailability>

    fun observePlaylists(): Flow<List<PlaylistSummary>>

    fun observePlaylist(id: PlaylistId): Flow<Playlist?>

    fun observeSavedTracks(): Flow<SavedTracksResult>

    suspend fun refreshAvailability()

    suspend fun refreshPlaylists()

    suspend fun refreshPlaylist(id: PlaylistId)

    suspend fun refreshSavedTracks()
}
