package com.mplayeraudio.core.player

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
    val artworkUri: String? = null,
)

enum class PlaybackPhase { Idle, Loading, Buffering, Playing, Paused, Failed, Ended }

sealed interface PlaybackError {
    val itemId: String?

    data class TrackUnavailable(override val itemId: String, val message: String) : PlaybackError
    data class StreamFailed(override val itemId: String, val message: String) : PlaybackError
    data class EngineCrashed(override val itemId: String?, val message: String) : PlaybackError
}

data class PlaybackQueueState(
    val queue: List<PlaybackQueueItem> = emptyList(),
    val currentIndex: Int? = null,
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val currentPositionMs: Long = 0L,
    val currentDurationMs: Long? = null,
    val bufferedPositionMs: Long = 0L,
    val playbackError: PlaybackError? = null,
) {
    val controlsEnabled: Boolean get() = queue.isNotEmpty()
    val isPlaying: Boolean get() = phase == PlaybackPhase.Playing
    val currentItem: PlaybackQueueItem? get() = currentIndex?.let(queue::getOrNull)
    val activeItemId: String? get() = currentItem?.id
    val playingItemId: String? get() = activeItemId.takeIf { isPlaying }
}

interface PlaybackQueueBridge : NowPlayingStripController {
    val playbackState: StateFlow<PlaybackQueueState>

    suspend fun replaceQueue(
        queue: List<PlaybackQueueItem>,
        startIndex: Int? = null,
        autoPlay: Boolean = false,
    )

    suspend fun playTrack(index: Int)

    fun acknowledgeError()

    fun shutdown()
}

interface PlayableUrlResolver {
    suspend fun getPlayableUrl(item: PlaybackQueueItem): String
}
