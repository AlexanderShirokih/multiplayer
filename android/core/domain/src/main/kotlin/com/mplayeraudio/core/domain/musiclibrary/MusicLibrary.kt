package com.mplayeraudio.core.domain.musiclibrary

import kotlinx.coroutines.flow.Flow

interface MusicLibrary {
    fun observeAvailability(): Flow<MusicServiceAvailability>

    fun observeAllPlaylists(): Flow<List<PlaylistSummary>>

    fun observePlaylist(ref: PlaylistRef): Flow<Playlist?>

    fun observeSavedTracks(): Flow<SavedTracksResult>

    suspend fun refreshAll()

    suspend fun refreshPlaylist(ref: PlaylistRef)

    suspend fun refreshSavedTracks()
}
