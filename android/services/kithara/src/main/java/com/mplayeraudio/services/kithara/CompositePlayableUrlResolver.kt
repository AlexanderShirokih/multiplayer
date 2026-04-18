package com.mplayeraudio.services.kithara

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlayableUrlResolver
import com.mplayeraudio.core.player.PlaybackQueueItem

internal class CompositePlayableUrlResolver(
    private val providers: Map<MusicProviderId, TrackStreamUrlProvider>,
) : PlayableUrlResolver {

    override suspend fun getPlayableUrl(item: PlaybackQueueItem): String {
        return when (val source = item.source) {
            is PlayableSource.Local -> source.uri
            is PlayableSource.Remote -> provider(source.provider).getStreamUrl(item.trackId)
        }
    }

    private fun provider(providerId: MusicProviderId): TrackStreamUrlProvider {
        return providers[providerId]
            ?: throw MusicLibraryException.ProviderError(
                code = "missing-provider",
                description = "No stream URL provider registered for $providerId.",
            )
    }
}
