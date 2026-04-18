package com.mplayeraudio.services.mediasession

import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlaybackQueueItem

internal fun PlaybackQueueItem.toMediaItem(): MediaItem {
    val mediaItemBuilder = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(subtitle)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .setExtras(extrasFor(this))
                .build(),
        )

    return when (val source = source) {
        is PlayableSource.Local -> mediaItemBuilder
            .setUri(source.uri)
            .build()

        is PlayableSource.Remote -> mediaItemBuilder.build()
    }
}

internal fun MediaItem.toQueueItem(): PlaybackQueueItem {
    val extras = mediaMetadata.extras
    return PlaybackQueueItem(
        id = mediaId,
        trackId = TrackId(mediaId),
        source = extras.toPlayableSource(fallbackUri = localConfiguration?.uri?.toString()),
        title = mediaMetadata.title?.toString().orEmpty(),
        subtitle = mediaMetadata.artist?.toString().orEmpty(),
        durationMs = extras?.getLong(MediaItemDurationMsKey) ?: 0L,
        artworkUri = mediaMetadata.artworkUri?.toString(),
    )
}

private fun extrasFor(item: PlaybackQueueItem): Bundle {
    return when (val source = item.source) {
        is PlayableSource.Local -> bundleOf(
            MediaItemDurationMsKey to item.durationMs,
            MediaItemSourceTypeKey to MediaItemSourceTypeLocal,
            MediaItemSourceLocalUriKey to source.uri,
        )

        is PlayableSource.Remote -> bundleOf(
            MediaItemDurationMsKey to item.durationMs,
            MediaItemSourceTypeKey to MediaItemSourceTypeRemote,
            MediaItemSourceRemoteProviderKey to source.provider.name,
        )
    }
}

private fun Bundle?.toPlayableSource(fallbackUri: String?): PlayableSource {
    val extras = this ?: return PlayableSource.Local(fallbackUri.orEmpty())
    return when (extras.getString(MediaItemSourceTypeKey)) {
        MediaItemSourceTypeRemote -> remoteSource(extras, fallbackUri)
        MediaItemSourceTypeLocal -> localSource(extras, fallbackUri)
        else -> PlayableSource.Local(fallbackUri.orEmpty())
    }
}

private fun remoteSource(extras: Bundle, fallbackUri: String?): PlayableSource {
    val provider = extras.getString(MediaItemSourceRemoteProviderKey)
        ?.let(::providerOrNull)
    return provider?.let(PlayableSource::Remote)
        ?: PlayableSource.Local(fallbackUri.orEmpty())
}

private fun localSource(extras: Bundle, fallbackUri: String?): PlayableSource {
    val uri = extras.getString(MediaItemSourceLocalUriKey)
        ?.takeUnless(String::isEmpty)
        ?: fallbackUri.orEmpty()
    return PlayableSource.Local(uri)
}

private fun providerOrNull(name: String): MusicProviderId? {
    return MusicProviderId.entries.firstOrNull { it.name == name }
}
