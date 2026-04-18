package com.mplayeraudio.services.devicemusic

import android.database.Cursor
import android.provider.MediaStore
import com.mplayeraudio.core.domain.musiclibrary.ArtistPreview
import com.mplayeraudio.core.domain.musiclibrary.PlaylistTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.Track
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.TrackPreview
import com.mplayeraudio.core.domain.musiclibrary.TrackRef

internal data class DeviceAudioTrack(
    val mediaId: Long,
    val contentUri: String,
    val entry: PlaylistTrackEntry,
)

internal data class DeviceMediaStoreRow(
    val mediaId: Long,
    val albumId: Long?,
    val title: String,
    val artistName: String,
    val durationMs: Long,
    val position: Int,
)

internal object DeviceMediaStoreMapper {
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
    )

    fun map(cursor: Cursor): List<DeviceAudioTrack> {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val albumIdIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        return buildList {
            var position = 0
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val albumId = cursor.getLong(albumIdIndex)
                val title = cursor.getString(titleIndex).orEmpty()
                val artistName = cursor.getString(artistIndex).orEmpty()
                val durationMs = cursor.getLong(durationIndex)
                mapRow(
                    DeviceMediaStoreRow(
                        mediaId = mediaId,
                        albumId = albumId.takeIf { it > 0L },
                        title = title,
                        artistName = artistName,
                        durationMs = durationMs,
                        position = position,
                    ),
                )?.let { mappedTrack ->
                    add(mappedTrack)
                    position += 1
                }
            }
        }
    }

    fun mapRow(row: DeviceMediaStoreRow): DeviceAudioTrack? {
        if (!shouldInclude(
                title = row.title,
                artistName = row.artistName,
                durationMs = row.durationMs,
            )
        ) {
            return null
        }

        val contentUri = "$DeviceMediaContentBaseUri/${row.mediaId}"

        return DeviceAudioTrack(
            mediaId = row.mediaId,
            contentUri = contentUri,
            entry = PlaylistTrackEntry(
                position = row.position,
                addedAt = null,
                originalIndex = null,
                originalShuffleIndex = null,
                isRecent = null,
                trackRef = TrackRef(
                    trackId = TrackId("device:${row.mediaId}"),
                    albumId = null,
                ),
                track = Track(
                    preview = TrackPreview(
                        ref = TrackRef(
                            trackId = TrackId("device:${row.mediaId}"),
                            albumId = null,
                        ),
                        title = row.title,
                        artists = row.artistName.toArtistPreviews(),
                        durationMs = row.durationMs.takeIf { it > 0L },
                        coverUriTemplate = row.albumId?.toAlbumArtworkUri(),
                        isAvailable = true,
                    ),
                    lyricsAvailable = false,
                    isAvailableForPremium = true,
                    isAvailableWithoutPermission = true,
                ),
            ),
        )
    }

    private fun shouldInclude(
        title: String,
        artistName: String,
        durationMs: Long,
    ): Boolean {
        return title.isNotBlank() &&
                durationMs > 0L &&
                artistName.isMeaningfulMetadata()
    }

    private fun String.toArtistPreviews(): List<ArtistPreview> {
        if (isBlank() || this == UnknownArtistName) {
            return emptyList()
        }

        return listOf(
            ArtistPreview(
                id = "device:$this",
                name = this,
            ),
        )
    }
}

private fun Long.toAlbumArtworkUri(): String {
    return "$DeviceAlbumArtBaseUri/$this"
}

private fun String?.isMeaningfulMetadata(): Boolean {
    val value = this?.trim().orEmpty()
    return value.isNotEmpty() &&
            value != UnknownArtistName
}

private const val UnknownArtistName = "<unknown>"
private const val DeviceMediaContentBaseUri = "content://media/external/audio/media"
private const val DeviceAlbumArtBaseUri = "content://media/external/audio/albumart"
