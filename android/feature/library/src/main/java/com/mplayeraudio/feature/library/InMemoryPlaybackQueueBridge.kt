package com.mplayeraudio.feature.library

import com.mplayeraudio.core.player.NowPlayingStripExternalState
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

internal class InMemoryPlaybackQueueBridge : PlaybackQueueBridge {

    private val stateFlow = MutableStateFlow(PlaybackQueueState())

    override val playbackState: Flow<PlaybackQueueState> = stateFlow.asStateFlow()

    override val state: Flow<NowPlayingStripExternalState> = playbackState.map { playbackState ->
        val currentItem = playbackState.currentItem
        NowPlayingStripExternalState(
            title = currentItem?.title.orEmpty(),
            subtitle = currentItem?.subtitle.orEmpty(),
            isPlaying = playbackState.isPlaying,
            currentPositionMs = playbackState.currentPositionMs,
            durationMs = currentItem?.durationMs ?: 0L,
            controlsEnabled = playbackState.controlsEnabled,
        )
    }

    override suspend fun replaceQueue(
        queue: List<PlaybackQueueItem>,
        startIndex: Int?,
        autoPlay: Boolean,
    ) {
        val previousState = stateFlow.value
        val preservedIndex = previousState.currentItem
            ?.let { currentItem ->
                queue.indexOfFirst { queuedItem -> queuedItem.id == currentItem.id }
                    .takeIf { index -> index >= 0 }
            }
        val nextIndex = startIndex ?: preservedIndex
        val currentPositionMs = if (nextIndex != null && nextIndex == preservedIndex) {
            previousState.currentPositionMs.coerceIn(0L, queue[nextIndex].durationMs.coerceAtLeast(0L))
        } else {
            0L
        }

        stateFlow.value = PlaybackQueueState(
            queue = queue,
            currentIndex = nextIndex,
            isPlaying = when {
                queue.isEmpty() -> false
                autoPlay && nextIndex != null -> true
                nextIndex != null && nextIndex == preservedIndex -> previousState.isPlaying
                else -> false
            },
            currentPositionMs = currentPositionMs,
            controlsEnabled = queue.isNotEmpty(),
        )
    }

    override suspend fun playTrack(index: Int) {
        val currentState = stateFlow.value
        if (index !in currentState.queue.indices) return

        stateFlow.value = currentState.copy(
            currentIndex = index,
            isPlaying = true,
            currentPositionMs = 0L,
            controlsEnabled = currentState.queue.isNotEmpty(),
        )
    }

    override suspend fun play() {
        val currentState = stateFlow.value
        if (currentState.queue.isEmpty()) return

        stateFlow.value = currentState.copy(
            currentIndex = currentState.currentIndex ?: 0,
            isPlaying = true,
            controlsEnabled = true,
        )
    }

    override suspend fun pause() {
        val currentState = stateFlow.value
        if (currentState.queue.isEmpty()) return

        stateFlow.value = currentState.copy(
            isPlaying = false,
        )
    }

    override suspend fun skipNext() {
        val currentState = stateFlow.value
        val currentIndex = currentState.currentIndex ?: return
        val nextIndex = (currentIndex + 1).coerceAtMost(currentState.queue.lastIndex)

        stateFlow.value = currentState.copy(
            currentIndex = nextIndex,
            currentPositionMs = 0L,
            isPlaying = true,
        )
    }

    override suspend fun skipPrevious() {
        val currentState = stateFlow.value
        val currentIndex = currentState.currentIndex ?: return

        if (currentState.currentPositionMs > RestartThresholdMs) {
            stateFlow.value = currentState.copy(currentPositionMs = 0L)
            return
        }

        val previousIndex = (currentIndex - 1).coerceAtLeast(0)
        stateFlow.value = currentState.copy(
            currentIndex = previousIndex,
            currentPositionMs = 0L,
            isPlaying = true,
        )
    }

    override suspend fun seekTo(positionMs: Long) {
        val currentState = stateFlow.value
        val currentItem = currentState.currentItem ?: return

        stateFlow.value = currentState.copy(
            currentPositionMs = positionMs.coerceIn(0L, currentItem.durationMs.coerceAtLeast(0L)),
        )
    }
}

private const val RestartThresholdMs = 3_000L
