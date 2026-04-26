package com.mplayeraudio.core.player

class CachingPlayableUrlResolver(
    private val delegate: PlayableUrlResolver,
) : PlayableUrlResolver {

    private val cache = StreamUrlCache()

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
