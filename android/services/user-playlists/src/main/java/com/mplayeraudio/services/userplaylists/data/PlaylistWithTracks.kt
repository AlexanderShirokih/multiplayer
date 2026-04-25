package com.mplayeraudio.services.userplaylists.data

import androidx.room.Embedded
import androidx.room.Relation

data class PlaylistWithTracks(
    @Embedded val playlist: UserPlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlist_id"
    )
    val tracks: List<UserPlaylistTrackEntity>
)
