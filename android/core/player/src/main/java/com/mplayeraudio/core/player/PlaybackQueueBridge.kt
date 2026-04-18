package com.mplayeraudio.core.player

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import kotlinx.coroutines.flow.Flow

sealed interface PlayableSource {
    data class Remote(
        val provider: MusicProviderId,
    ) : PlayableSource

    data class Local(
        val uri: String,
    ) : PlayableSource
}

data class PlaybackQueueItem(
    val id: String,
    val trackId: TrackId,
    val source: PlayableSource,
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

interface PlayableUrlResolver {
    suspend fun getPlayableUrl(item: PlaybackQueueItem): String
}
