package com.mplayeraudio.feature.library

import com.mplayeraudio.core.player.NowPlayingStripExternalState
import com.mplayeraudio.core.player.PlaybackPhase
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

internal class InMemoryPlaybackQueueBridge : PlaybackQueueBridge {

    private val stateFlow = MutableStateFlow(PlaybackQueueState())

    override val playbackState: StateFlow<PlaybackQueueState> = stateFlow.asStateFlow()

    override val state: Flow<NowPlayingStripExternalState> = playbackState.map { ps ->
        val currentItem = ps.currentItem
        NowPlayingStripExternalState(
            title = currentItem?.title.orEmpty(),
            subtitle = currentItem?.subtitle.orEmpty(),
            isPlaying = ps.isPlaying,
            currentPositionMs = ps.currentPositionMs,
            durationMs = ps.currentDurationMs ?: currentItem?.durationMs ?: 0L,
            controlsEnabled = ps.controlsEnabled,
        )
    }

    override suspend fun replaceQueue(
        queue: List<PlaybackQueueItem>,
        startIndex: Int?,
        autoPlay: Boolean,
    ) {
        val previous = stateFlow.value
        val preservedIndex = previous.currentItem
            ?.let { curr -> queue.indexOfFirst { it.id == curr.id }.takeIf { it >= 0 } }
        val nextIndex = startIndex ?: preservedIndex
        val preservingCurrentTrack = nextIndex != null && nextIndex == preservedIndex
        val currentPositionMs = if (preservingCurrentTrack && nextIndex != null) {
            previous.currentPositionMs.coerceIn(0L, queue[nextIndex].durationMs.coerceAtLeast(0L))
        } else {
            0L
        }
        val phase = when {
            queue.isEmpty() -> PlaybackPhase.Idle
            autoPlay && nextIndex != null -> PlaybackPhase.Playing
            preservingCurrentTrack -> previous.phase
            else -> PlaybackPhase.Idle
        }

        stateFlow.value = PlaybackQueueState(
            queue = queue,
            currentIndex = nextIndex,
            phase = phase,
            currentPositionMs = currentPositionMs,
        )
    }

    override suspend fun playTrack(index: Int) {
        val current = stateFlow.value
        if (index !in current.queue.indices) return
        stateFlow.value = current.copy(
            currentIndex = index,
            phase = PlaybackPhase.Playing,
            currentPositionMs = 0L,
        )
    }

    override suspend fun play() {
        val current = stateFlow.value
        if (current.queue.isEmpty()) return
        stateFlow.value = current.copy(
            currentIndex = current.currentIndex ?: 0,
            phase = PlaybackPhase.Playing,
        )
    }

    override suspend fun pause() {
        val current = stateFlow.value
        if (current.queue.isEmpty()) return
        stateFlow.value = current.copy(phase = PlaybackPhase.Paused)
    }

    override suspend fun skipNext() {
        val current = stateFlow.value
        val currentIndex = current.currentIndex ?: return
        val nextIndex = (currentIndex + 1).coerceAtMost(current.queue.lastIndex)
        stateFlow.value = current.copy(
            currentIndex = nextIndex,
            currentPositionMs = 0L,
            phase = PlaybackPhase.Playing,
        )
    }

    override suspend fun skipPrevious() {
        val current = stateFlow.value
        val currentIndex = current.currentIndex ?: return
        if (current.currentPositionMs > RestartThresholdMs) {
            stateFlow.value = current.copy(currentPositionMs = 0L)
            return
        }
        val previousIndex = (currentIndex - 1).coerceAtLeast(0)
        stateFlow.value = current.copy(
            currentIndex = previousIndex,
            currentPositionMs = 0L,
            phase = PlaybackPhase.Playing,
        )
    }

    override suspend fun seekTo(positionMs: Long) {
        val current = stateFlow.value
        val currentItem = current.currentItem ?: return
        stateFlow.value = current.copy(
            currentPositionMs = positionMs.coerceIn(0L, currentItem.durationMs.coerceAtLeast(0L)),
        )
    }

    override fun acknowledgeError() {
        stateFlow.value = stateFlow.value.copy(playbackError = null)
    }

    override fun shutdown() {
        stateFlow.value = PlaybackQueueState()
    }
}

private const val RestartThresholdMs = 3_000L
