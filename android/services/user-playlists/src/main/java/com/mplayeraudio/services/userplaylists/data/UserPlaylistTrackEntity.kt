package com.mplayeraudio.services.userplaylists.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = UserPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["playlist_id"]),
    ]
)
data class UserPlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    
    @ColumnInfo(name = "url")
    val url: String,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "artist")
    val artist: String?,
    
    @ColumnInfo(name = "added_at")
    val addedAt: Long,
)
