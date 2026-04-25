package com.mplayeraudio.services.userplaylists.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class UserPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
