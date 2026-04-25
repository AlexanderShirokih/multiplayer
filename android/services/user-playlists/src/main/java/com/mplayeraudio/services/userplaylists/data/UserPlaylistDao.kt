package com.mplayeraudio.services.userplaylists.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY created_at DESC")
    fun observePlaylistsWithTracks(): Flow<List<PlaylistWithTracks>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylistWithTracks(playlistId: Long): Flow<PlaylistWithTracks?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: UserPlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: UserPlaylistTrackEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE id = :trackId AND playlist_id = :playlistId")
    suspend fun deleteTrack(playlistId: Long, trackId: Long)

    @Query("SELECT MAX(id) FROM playlists")
    suspend fun getMaxPlaylistId(): Long?
    
    @Query("SELECT url FROM playlist_tracks WHERE id = :trackId")
    suspend fun getTrackUrl(trackId: Long): String?
}
