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

internal object DeviceMediaStoreMapper {
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
    )

    fun map(cursor: Cursor): List<DeviceAudioTrack> {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

        return buildList {
            var position = 0
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val title = cursor.getString(titleIndex).orEmpty()
                val artistName = cursor.getString(artistIndex).orEmpty()
                val durationMs = cursor.getLong(durationIndex)
                mapRow(
                    mediaId = mediaId,
                    title = title,
                    artistName = artistName,
                    durationMs = durationMs,
                    position = position,
                )?.let { mappedTrack ->
                    add(mappedTrack)
                    position += 1
                }
            }
        }
    }

    fun mapRow(
        mediaId: Long,
        title: String,
        artistName: String,
        durationMs: Long,
        position: Int,
    ): DeviceAudioTrack? {
        if (!shouldInclude(
                title = title,
                artistName = artistName,
                durationMs = durationMs,
            )
        ) {
            return null
        }

        val contentUri = "$DeviceMediaContentBaseUri/$mediaId"

        return DeviceAudioTrack(
            mediaId = mediaId,
            contentUri = contentUri,
            entry = PlaylistTrackEntry(
                position = position,
                addedAt = null,
                originalIndex = null,
                originalShuffleIndex = null,
                isRecent = null,
                trackRef = TrackRef(
                    trackId = TrackId("device:$mediaId"),
                    albumId = null,
                ),
                track = Track(
                    preview = TrackPreview(
                        ref = TrackRef(
                            trackId = TrackId("device:$mediaId"),
                            albumId = null,
                        ),
                        title = title,
                        artists = artistName.toArtistPreviews(),
                        durationMs = durationMs.takeIf { it > 0L },
                        coverUriTemplate = null,
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

private fun String?.isMeaningfulMetadata(): Boolean {
    val value = this?.trim().orEmpty()
    return value.isNotEmpty() &&
            value != UnknownArtistName
}

private const val UnknownArtistName = "<unknown>"
private const val DeviceMediaContentBaseUri = "content://media/external/audio/media"
