package com.mplayeraudio.services.mediasession

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class PlayerMediaSessionCallback : MediaSession.Callback {

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> {
        return Futures.immediateFuture(resolveMediaItemsForSession(mediaItems))
    }
}

internal fun resolveMediaItemsForSession(mediaItems: List<MediaItem>): List<MediaItem> {
    return mediaItems.map(MediaItem::resolveForSession)
}

private fun MediaItem.resolveForSession(): MediaItem {
    val extras = mediaMetadata.extras
    val uri = resolveSessionLocalUri(
        hasLocalConfiguration = localConfiguration != null,
        sourceType = extras?.getString(MediaItemSourceTypeKey),
        localUri = extras?.getString(MediaItemSourceLocalUriKey),
    )
        ?: return this
    return buildUpon()
        .setUri(uri)
        .build()
}

internal fun resolveSessionLocalUri(
    hasLocalConfiguration: Boolean,
    sourceType: String?,
    localUri: String?,
): String? {
    return if (
        hasLocalConfiguration ||
        sourceType != MediaItemSourceTypeLocal ||
        localUri.isNullOrBlank()
    ) {
        null
    } else {
        localUri
    }
}
