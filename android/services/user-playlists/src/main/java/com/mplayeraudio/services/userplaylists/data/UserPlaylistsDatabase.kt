package com.mplayeraudio.services.userplaylists.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserPlaylistEntity::class,
        UserPlaylistTrackEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class UserPlaylistsDatabase : RoomDatabase() {
    abstract fun userPlaylistDao(): UserPlaylistDao
}
