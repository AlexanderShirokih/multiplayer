package com.mplayeraudio.core.player

internal fun interface MonotonicClock {
    fun nowMs(): Long
}

internal class StreamUrlCache(
    private val ttlMs: Long = StreamUrlTtlMs,
    private val maxSize: Int = DefaultMaxSize,
    private val clock: MonotonicClock = MonotonicClock(System::currentTimeMillis),
) {
    private data class Entry(val url: String, val resolvedAtMs: Long)

    private val entries = object : LinkedHashMap<String, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean {
            return size > maxSize
        }
    }

    fun get(itemId: String): String? {
        val entry = entries[itemId] ?: return null
        return if (isExpired(entry)) {
            entries.remove(itemId)
            null
        } else {
            entry.url
        }
    }

    fun put(itemId: String, url: String) {
        entries[itemId] = Entry(url = url, resolvedAtMs = clock.nowMs())
    }

    fun remove(itemId: String) {
        entries.remove(itemId)
    }

    fun clear() {
        entries.clear()
    }

    private fun isExpired(entry: Entry): Boolean {
        return clock.nowMs() - entry.resolvedAtMs > ttlMs
    }

    private companion object {
        const val DefaultMaxSize = 64
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

private const val StreamUrlTtlMs = 25L * 60L * 1_000L
