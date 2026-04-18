package com.mplayeraudio.services.mediasession

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mplayeraudio.core.player.NowPlayingStripExternalState
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class MediaControllerPlaybackQueueBridge(
    private val context: Context,
    private val scope: CoroutineScope,
) : PlaybackQueueBridge {

    private val stateFlow = MutableStateFlow(PlaybackQueueState())
    private var controllerDeferred: MediaController? = null
    private var selectedQueueItemId: String? = null

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

    init {
        scope.launch { startPositionTicker() }
    }

    override suspend fun replaceQueue(
        queue: List<PlaybackQueueItem>,
        startIndex: Int?,
        autoPlay: Boolean,
    ) {
        val controller = controller()
        val replacement = planQueueReplacement(
            previousCurrentItemId = controller.currentMediaItem?.mediaId,
            previousPositionMs = controller.currentPosition.coerceAtLeast(0L),
            wasPlaying = controller.isPlaying,
            nextQueue = queue,
            requestedStartIndex = startIndex,
            autoPlay = autoPlay,
        )
        selectedQueueItemId = replacement.selectedItemId
        controller.setMediaItems(
            queue.map(PlaybackQueueItem::toMediaItem),
            replacement.startIndex ?: 0,
            replacement.startPositionMs,
        )
        controller.prepare()
        if (replacement.shouldPlay) {
            controller.play()
        } else {
            controller.pause()
        }
        publishState(controller, queueOverride = queue)
    }

    override suspend fun playTrack(index: Int) {
        val controller = controller()
        selectedQueueItemId = stateFlow.value.queue.getOrNull(index)?.id
        controller.seekToDefaultPosition(index)
        controller.play()
    }

    override suspend fun play() {
        if (selectedQueueItemId == null) {
            selectedQueueItemId = stateFlow.value.queue.firstOrNull()?.id
        }
        controller().play()
    }

    override suspend fun pause() {
        controller().pause()
    }

    override suspend fun skipNext() {
        val currentState = stateFlow.value
        val nextIndex = currentState.currentIndex
            ?.plus(1)
            ?.coerceAtMost(currentState.queue.lastIndex)
            ?: 0
        selectedQueueItemId = currentState.queue.getOrNull(nextIndex)?.id
        controller().seekToNextMediaItem()
    }

    override suspend fun skipPrevious() {
        val controller = controller()
        val currentState = stateFlow.value
        val currentIndex = currentState.currentIndex ?: return
        if (controller.currentPosition > RestartThresholdMs || currentIndex == 0) {
            controller.seekTo(0L)
        } else {
            selectedQueueItemId = currentState.queue[currentIndex - 1].id
            controller.seekToPreviousMediaItem()
        }
    }

    override suspend fun seekTo(positionMs: Long) {
        controller().seekTo(positionMs)
    }

    private suspend fun controller(): MediaController {
        controllerDeferred?.let { return it }

        val future = MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, PlayerMediaSessionService::class.java)),
        ).buildAsync()
        val controller = future.await()
        controller.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    publishState(player)
                }
            },
        )
        publishState(controller)
        controllerDeferred = controller
        return controller
    }

    private fun publishState(
        player: Player,
        queueOverride: List<PlaybackQueueItem>? = null,
    ) {
        val queue = queueOverride ?: List(player.mediaItemCount) { index ->
            player.getMediaItemAt(index).toQueueItem()
        }
        selectedQueueItemId = resolveSelectedQueueItemId(
            existingSelectedItemId = selectedQueueItemId,
            playerCurrentMediaItemId = player.currentMediaItem?.mediaId,
            playerPlayWhenReady = player.playWhenReady,
            playerIsPlaying = player.isPlaying,
            playerCurrentPositionMs = player.currentPosition.coerceAtLeast(0L),
            playerPlaybackState = player.playbackState,
            queue = queue,
        )
        stateFlow.value = PlaybackQueueState(
            queue = queue,
            currentIndex = queue.currentIndexFor(selectedQueueItemId),
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            controlsEnabled = queue.isNotEmpty(),
        )
    }

    private suspend fun startPositionTicker() {
        while (true) {
            delay(PositionUpdateIntervalMs)
            val controller = controllerDeferred
            if (controller != null && controller.isPlaying) {
                publishState(controller)
            }
        }
    }
}

internal fun mediaSessionBridgeScope(): CoroutineScope {
    return CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

internal data class QueueReplacementPlan(
    val startIndex: Int?,
    val startPositionMs: Long,
    val shouldPlay: Boolean,
    val selectedItemId: String?,
)

internal fun planQueueReplacement(
    previousCurrentItemId: String?,
    previousPositionMs: Long,
    wasPlaying: Boolean,
    nextQueue: List<PlaybackQueueItem>,
    requestedStartIndex: Int?,
    autoPlay: Boolean,
): QueueReplacementPlan {
    val preservedIndex = previousCurrentItemId
        ?.let { currentItemId ->
            nextQueue.indexOfFirst { item -> item.id == currentItemId }
                .takeIf { index -> index >= 0 }
        }
    val nextIndex = requestedStartIndex ?: preservedIndex
    val preservingCurrentTrack = nextIndex != null && nextIndex == preservedIndex
    val startPositionMs = resolvedStartPositionMs(
        nextQueue = nextQueue,
        nextIndex = nextIndex,
        preservingCurrentTrack = preservingCurrentTrack,
        previousPositionMs = previousPositionMs,
    )
    val shouldPlay = replacementShouldPlay(
        nextQueue = nextQueue,
        nextIndex = nextIndex,
        autoPlay = autoPlay,
        preservingCurrentTrack = preservingCurrentTrack,
        wasPlaying = wasPlaying,
    )
    val selectedItemId = replacementSelectedItemId(
        nextQueue = nextQueue,
        nextIndex = nextIndex,
        requestedStartIndex = requestedStartIndex,
        preservingCurrentTrack = preservingCurrentTrack,
    )

    return QueueReplacementPlan(
        startIndex = nextIndex,
        startPositionMs = startPositionMs,
        shouldPlay = shouldPlay,
        selectedItemId = selectedItemId,
    )
}

private fun resolvedStartPositionMs(
    nextQueue: List<PlaybackQueueItem>,
    nextIndex: Int?,
    preservingCurrentTrack: Boolean,
    previousPositionMs: Long,
): Long {
    if (!preservingCurrentTrack || nextIndex == null) {
        return 0L
    }

    return previousPositionMs.coerceIn(
        0L,
        nextQueue[nextIndex].durationMs.coerceAtLeast(0L),
    )
}

private fun replacementShouldPlay(
    nextQueue: List<PlaybackQueueItem>,
    nextIndex: Int?,
    autoPlay: Boolean,
    preservingCurrentTrack: Boolean,
    wasPlaying: Boolean,
): Boolean {
    return when {
        nextQueue.isEmpty() -> false
        autoPlay && nextIndex != null -> true
        preservingCurrentTrack -> wasPlaying
        else -> false
    }
}

private fun replacementSelectedItemId(
    nextQueue: List<PlaybackQueueItem>,
    nextIndex: Int?,
    requestedStartIndex: Int?,
    preservingCurrentTrack: Boolean,
): String? {
    val shouldSelectItem = requestedStartIndex != null || preservingCurrentTrack
    return if (shouldSelectItem && nextIndex != null) {
        nextQueue[nextIndex].id
    } else {
        null
    }
}

internal fun resolveSelectedQueueItemId(
    existingSelectedItemId: String?,
    playerCurrentMediaItemId: String?,
    playerPlayWhenReady: Boolean,
    playerIsPlaying: Boolean,
    playerCurrentPositionMs: Long,
    playerPlaybackState: Int,
    queue: List<PlaybackQueueItem>,
): String? {
    val currentPlayerItemId = playerCurrentMediaItemId
        ?.takeIf { mediaItemId -> queue.containsMediaItemId(mediaItemId) }
    val selectedItemId = existingSelectedItemId
        ?.takeIf { mediaItemId -> queue.containsMediaItemId(mediaItemId) }

    return if (
        currentPlayerItemId != null &&
        hasMeaningfulCurrentSelection(
            playWhenReady = playerPlayWhenReady,
            isPlaying = playerIsPlaying,
            currentPositionMs = playerCurrentPositionMs,
            playbackState = playerPlaybackState,
        )
    ) {
        currentPlayerItemId
    } else {
        selectedItemId
    }
}

internal fun hasMeaningfulCurrentSelection(
    playWhenReady: Boolean,
    isPlaying: Boolean,
    currentPositionMs: Long,
    playbackState: Int,
): Boolean {
    return playWhenReady ||
        isPlaying ||
        currentPositionMs > 0L ||
        playbackState == Player.STATE_ENDED
}

private fun List<PlaybackQueueItem>.currentIndexFor(
    selectedItemId: String?,
): Int? {
    val index = indexOfFirst { item -> item.id == selectedItemId }
    return index.takeIf { it >= 0 }
}

private fun List<PlaybackQueueItem>.containsMediaItemId(
    mediaItemId: String,
): Boolean {
    return any { item -> item.id == mediaItemId }
}
