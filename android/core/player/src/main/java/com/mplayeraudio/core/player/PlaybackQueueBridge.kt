package com.mplayeraudio.core.player

import kotlinx.coroutines.flow.Flow

data class PlaybackQueueItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
)

data class PlaybackQueueState(
    val queue: List<PlaybackQueueItem> = emptyList(),
    val currentIndex: Int? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val controlsEnabled: Boolean = queue.isNotEmpty(),
) {
    val currentItem: PlaybackQueueItem?
        get() = currentIndex?.let(queue::getOrNull)
}

interface PlaybackQueueBridge : NowPlayingStripController {
    val playbackState: Flow<PlaybackQueueState>

    suspend fun replaceQueue(
        queue: List<PlaybackQueueItem>,
        startIndex: Int? = null,
        autoPlay: Boolean = false,
    )

    suspend fun playTrack(index: Int)
}
