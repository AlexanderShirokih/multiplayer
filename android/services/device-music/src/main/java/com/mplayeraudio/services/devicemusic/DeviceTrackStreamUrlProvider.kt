package com.mplayeraudio.services.devicemusic

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import java.io.File

internal class DeviceTrackStreamUrlProvider(
    private val contentResolver: ContentResolver,
) : TrackStreamUrlProvider {

    override suspend fun getStreamUrl(trackId: TrackId): String {
        val mediaId = trackId.value
            .substringAfter(DeviceTrackIdPrefix, missingDelimiterValue = "")
            .toLongOrNull()
            ?: throw MusicLibraryException.ProviderError(
                code = "device-invalid-track-id",
                description = "Track id ${trackId.value} is not a device media id.",
            )

        val absolutePath = queryAbsolutePath(mediaId)
            ?: throw MusicLibraryException.ProviderError(
                code = "device-track-not-found",
                description = "No file path for media id $mediaId.",
            )

        return File(absolutePath).toURI().toString()
    }

    private fun queryAbsolutePath(mediaId: Long): String? {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            mediaId,
        )
        return contentResolver.query(
            uri,
            arrayOf(MediaStore.Audio.Media.DATA),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)?.takeIf { it.isNotBlank() }
        }
    }
}

private const val DeviceTrackIdPrefix = "device:"
