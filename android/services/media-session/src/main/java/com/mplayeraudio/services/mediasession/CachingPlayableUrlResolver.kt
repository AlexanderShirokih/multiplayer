package com.mplayeraudio.services.mediasession

import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlayableUrlResolver
import com.mplayeraudio.core.player.PlaybackQueueItem

internal class CachingPlayableUrlResolver(
    private val delegate: PlayableUrlResolver,
    private val cache: StreamUrlCache = StreamUrlCache(),
) : PlayableUrlResolver {

    override suspend fun getPlayableUrl(item: PlaybackQueueItem): String {
        cache.get(item.id)?.let { return it }

        val url = delegate.getPlayableUrl(item)
        if (item.source is PlayableSource.Remote) {
            cache.put(item.id, url)
        }
        return url
    }

    fun invalidate(itemId: String) {
        cache.remove(itemId)
    }

    fun clear() {
        cache.clear()
    }
}
