package com.mplayeraudio.services.kithara

import android.util.Log
import com.mplayeraudio.core.player.CachingPlayableUrlResolver
import com.mplayeraudio.core.player.NowPlayingStripExternalState
import com.mplayeraudio.core.player.PlayableUrlResolver
import com.mplayeraudio.core.player.PlaybackError
import com.mplayeraudio.core.player.PlaybackPhase
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class PlaybackQueueController(
    private val engine: AudioPlaybackEngine,
    urlResolver: PlayableUrlResolver,
    scope: CoroutineScope,
    private val loadTimeoutMs: Long = DefaultLoadTimeoutMs,
) : PlaybackQueueBridge {

    private val cachingResolver = CachingPlayableUrlResolver(delegate = urlResolver)

    private val _state = MutableStateFlow(PlaybackQueueState())
    override val playbackState: StateFlow<PlaybackQueueState> = _state.asStateFlow()

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

    // All internal coroutines (state/event collection, watchdog, ticker, auto-skip)
    // are launched in childScope. shutdown() cancels the whole scope at once,
    // which makes advanceUntilIdle() safe in tests.
    private val childScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

    private var failedCountSinceLastSuccess = 0
    private var watchdogJob: Job? = null
    private var tickerJob: Job? = null

    init {
        childScope.launch { collectEngineState() }
        childScope.launch { collectEngineEvents() }
    }

    override suspend fun replaceQueue(
        queue: List<PlaybackQueueItem>,
        startIndex: Int?,
        autoPlay: Boolean,
    ) {
        val plan = planReplacement(queue, startIndex, autoPlay, _state.value)
        applyReplacementPlan(queue, plan)
    }

    private fun planReplacement(
        queue: List<PlaybackQueueItem>,
        startIndex: Int?,
        autoPlay: Boolean,
        previous: PlaybackQueueState,
    ): QueueReplacementPlan {
        val preservedIndex = previous.currentItem
            ?.let { curr -> queue.indexOfFirst { it.id == curr.id }.takeIf { it >= 0 } }
        val nextIndex = startIndex ?: preservedIndex ?: if (queue.isNotEmpty()) 0 else null
        val preservingCurrentTrack = nextIndex != null && nextIndex == preservedIndex
        val startPositionMs = resolvedStartPosition(queue, nextIndex, preservingCurrentTrack, previous)
        val shouldPlay = resolvedShouldPlay(queue, nextIndex, autoPlay, preservingCurrentTrack, previous)
        return QueueReplacementPlan(nextIndex, startPositionMs, shouldPlay, preservingCurrentTrack)
    }

    private fun resolvedStartPosition(
        queue: List<PlaybackQueueItem>,
        nextIndex: Int?,
        preservingCurrentTrack: Boolean,
        previous: PlaybackQueueState,
    ): Long {
        if (!preservingCurrentTrack || nextIndex == null) return 0L
        return previous.currentPositionMs.coerceIn(0L, queue[nextIndex].durationMs.coerceAtLeast(0L))
    }

    private fun resolvedShouldPlay(
        queue: List<PlaybackQueueItem>,
        nextIndex: Int?,
        autoPlay: Boolean,
        preservingCurrentTrack: Boolean,
        previous: PlaybackQueueState,
    ): Boolean = when {
        queue.isEmpty() -> false
        autoPlay && nextIndex != null -> true
        preservingCurrentTrack -> previous.isPlaying
        else -> false
    }

    private suspend fun applyReplacementPlan(
        queue: List<PlaybackQueueItem>,
        plan: QueueReplacementPlan,
    ) {
        val previous = _state.value
        _state.value = PlaybackQueueState(
            queue = queue,
            currentIndex = plan.nextIndex,
            phase = if (queue.isEmpty() || !plan.preservingCurrentTrack) PlaybackPhase.Idle else previous.phase,
            currentPositionMs = plan.startPositionMs,
            currentDurationMs = if (plan.preservingCurrentTrack) previous.currentDurationMs else null,
        )

        if (queue.isEmpty() || !plan.preservingCurrentTrack) {
            cancelWatchdog()
            updateTicker(isPlaying = false)
            engine.stop()
        }

        if (plan.shouldPlay && plan.nextIndex != null) {
            failedCountSinceLastSuccess = 0
            loadAndPlay(plan.nextIndex, plan.startPositionMs)
        }
    }

    override suspend fun playTrack(index: Int) {
        val currentQueue = _state.value.queue
        if (index !in currentQueue.indices) return
        failedCountSinceLastSuccess = 0
        _state.update {
            it.copy(
                currentIndex = index,
                phase = PlaybackPhase.Loading,
                currentPositionMs = 0L,
                currentDurationMs = null,
                playbackError = null,
            )
        }
        loadAndPlay(index, 0L)
    }

    override suspend fun play() {
        val current = _state.value
        if (current.queue.isEmpty()) return
        val index = current.currentIndex ?: 0
        val loadedItemId = engine.engineState.value.currentItemId
        val currentItemId = current.queue.getOrNull(index)?.id

        if (loadedItemId == currentItemId && loadedItemId != null) {
            engine.play()
            _state.update { it.copy(phase = PlaybackPhase.Playing) }
            updateTicker(isPlaying = true)
        } else {
            loadAndPlay(index, current.currentPositionMs)
        }
    }

    override suspend fun pause() {
        engine.pause()
        cancelWatchdog()
        _state.update { current ->
            val newPhase = when (current.phase) {
                PlaybackPhase.Playing,
                PlaybackPhase.Buffering,
                PlaybackPhase.Loading -> PlaybackPhase.Paused
                else -> current.phase
            }
            current.copy(phase = newPhase)
        }
        updateTicker(isPlaying = false)
    }

    override suspend fun skipNext() {
        val current = _state.value
        val nextIndex = (current.currentIndex ?: -1) + 1
        if (nextIndex > current.queue.lastIndex) {
            markEnded()
            return
        }
        failedCountSinceLastSuccess = 0
        _state.update {
            it.copy(
                currentIndex = nextIndex,
                phase = PlaybackPhase.Loading,
                currentPositionMs = 0L,
                currentDurationMs = null,
                playbackError = null,
            )
        }
        loadAndPlay(nextIndex, 0L)
    }

    override suspend fun skipPrevious() {
        val current = _state.value
        val currentIndex = current.currentIndex ?: return
        if (current.currentPositionMs > RestartThresholdMs || currentIndex == 0) {
            engine.seekTo(0L)
            _state.update { it.copy(currentPositionMs = 0L) }
            return
        }
        val prevIndex = currentIndex - 1
        _state.update {
            it.copy(
                currentIndex = prevIndex,
                phase = PlaybackPhase.Loading,
                currentPositionMs = 0L,
                currentDurationMs = null,
                playbackError = null,
            )
        }
        loadAndPlay(prevIndex, 0L)
    }

    override suspend fun seekTo(positionMs: Long) {
        val current = _state.value
        val currentItem = current.currentItem ?: return
        val durationCap = current.currentDurationMs ?: currentItem.durationMs
        val clamped = positionMs.coerceIn(0L, durationCap.coerceAtLeast(0L))
        engine.seekTo(clamped)
        _state.update { it.copy(currentPositionMs = clamped) }
    }

    override fun acknowledgeError() {
        _state.update { it.copy(playbackError = null) }
    }

    override fun shutdown() {
        childScope.cancel()
        engine.stop()
        _state.value = PlaybackQueueState()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadAndPlay(index: Int, startPositionMs: Long) {
        val item = _state.value.queue.getOrNull(index) ?: return
        _state.update { it.copy(phase = PlaybackPhase.Loading, playbackError = null) }
        startWatchdog(item.id)

        try {
            val url = cachingResolver.getPlayableUrl(item)
            engine.loadTrack(AudioTrackRequest(id = item.id, url = url), autoPlay = true)
            if (startPositionMs > 0L) {
                engine.seekTo(startPositionMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load track ${item.id}", e)
            cancelWatchdog()
            val message = e.message?.takeUnless(String::isBlank) ?: "Load failed"
            onItemFailed(item.id, PlaybackError.TrackUnavailable(item.id, message))
        }
    }

    private suspend fun collectEngineState() {
        engine.engineState.collect { engineState ->
            _state.update { current ->
                val isForCurrentItem = engineState.currentItemId != null &&
                    engineState.currentItemId == current.currentItem?.id
                if (!isForCurrentItem) return@update current

                val newPhase = when {
                    current.phase == PlaybackPhase.Failed ||
                        current.phase == PlaybackPhase.Idle -> current.phase
                    engineState.isPlaying -> PlaybackPhase.Playing
                    engineState.status == AudioEngineStatus.ReadyToPlay && !engineState.isPlaying ->
                        if (current.phase == PlaybackPhase.Playing) PlaybackPhase.Paused else current.phase
                    else -> current.phase
                }

                if (newPhase == PlaybackPhase.Playing && current.phase != PlaybackPhase.Playing) {
                    failedCountSinceLastSuccess = 0
                    cancelWatchdog()
                }

                current.copy(
                    phase = newPhase,
                    currentPositionMs = engineState.currentPositionMs,
                    bufferedPositionMs = engineState.bufferedPositionMs,
                    currentDurationMs = engineState.durationMs ?: current.currentDurationMs,
                )
            }
            updateTicker(isPlaying = _state.value.isPlaying)
        }
    }

    private suspend fun collectEngineEvents() {
        engine.events.collect { event ->
            when (event) {
                is AudioEngineEvent.PlayedToEnd -> onPlayedToEnd(event.itemId)
                is AudioEngineEvent.ItemFailed ->
                    onItemFailed(event.itemId, event.reason.toPlaybackError(event.itemId))
                is AudioEngineEvent.EngineFailed -> {
                    val currentItemId = _state.value.currentItem?.id
                    onItemFailed(
                        itemId = currentItemId ?: "",
                        error = PlaybackError.EngineCrashed(currentItemId, event.reason.toString()),
                    )
                }
                is AudioEngineEvent.DurationDiscovered -> {
                    if (event.itemId == _state.value.currentItem?.id) {
                        _state.update { it.copy(currentDurationMs = event.durationMs) }
                    }
                }
                is AudioEngineEvent.CurrentItemChanged -> Unit
            }
        }
    }

    private suspend fun onPlayedToEnd(itemId: String) {
        val current = _state.value
        val currentItem = current.currentItem
        if (currentItem == null || itemId != currentItem.id) {
            return
        }

        val nextIndex = (current.currentIndex ?: -1) + 1
        if (nextIndex > current.queue.lastIndex) {
            markEnded()
        } else {
            failedCountSinceLastSuccess = 0
            _state.update {
                it.copy(
                    currentIndex = nextIndex,
                    phase = PlaybackPhase.Loading,
                    currentPositionMs = 0L,
                    currentDurationMs = null,
                )
            }
            loadAndPlay(nextIndex, 0L)
        }
    }

    private fun onItemFailed(itemId: String, error: PlaybackError) {
        val current = _state.value
        val currentItem = current.currentItem ?: return
        if (itemId.isNotEmpty() && itemId != currentItem.id) return

        cancelWatchdog()
        updateTicker(isPlaying = false)
        failedCountSinceLastSuccess++

        _state.update { it.copy(phase = PlaybackPhase.Failed, playbackError = error) }

        val nextIndex = (current.currentIndex ?: -1) + 1
        if (nextIndex <= current.queue.lastIndex && failedCountSinceLastSuccess < current.queue.size) {
            childScope.launch {
                delay(AutoSkipDelayMs)
                if (_state.value.phase == PlaybackPhase.Failed) {
                    _state.update {
                        it.copy(
                            currentIndex = nextIndex,
                            phase = PlaybackPhase.Loading,
                            currentPositionMs = 0L,
                            currentDurationMs = null,
                        )
                    }
                    loadAndPlay(nextIndex, 0L)
                }
            }
        }
    }

    private fun markEnded() {
        cancelWatchdog()
        updateTicker(isPlaying = false)
        _state.update { it.copy(phase = PlaybackPhase.Ended, currentPositionMs = 0L) }
    }

    private fun startWatchdog(expectedItemId: String) {
        cancelWatchdog()
        watchdogJob = childScope.launch {
            delay(loadTimeoutMs)
            val current = _state.value
            if ((current.phase == PlaybackPhase.Loading || current.phase == PlaybackPhase.Buffering) &&
                current.currentItem?.id == expectedItemId
            ) {
                onItemFailed(
                    itemId = expectedItemId,
                    error = PlaybackError.StreamFailed(expectedItemId, "Load timeout"),
                )
            }
        }
    }

    private fun cancelWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private fun updateTicker(isPlaying: Boolean) {
        if (!isPlaying) {
            tickerJob?.cancel()
            tickerJob = null
            return
        }
        if (tickerJob?.isActive == true) return
        tickerJob = childScope.launch {
            while (true) {
                delay(PositionUpdateIntervalMs)
                _state.update { it.copy(currentPositionMs = engine.engineState.value.currentPositionMs) }
            }
        }
    }

    private companion object {
        const val TAG = "PlaybackQueueController"
        const val DefaultLoadTimeoutMs = 30_000L
        const val RestartThresholdMs = 3_000L
        const val AutoSkipDelayMs = 100L
        const val PositionUpdateIntervalMs = 500L
    }
}

private data class QueueReplacementPlan(
    val nextIndex: Int?,
    val startPositionMs: Long,
    val shouldPlay: Boolean,
    val preservingCurrentTrack: Boolean,
)

private fun AudioEngineError.toPlaybackError(itemId: String): PlaybackError = when (this) {
    is AudioEngineError.LoadFailed -> PlaybackError.TrackUnavailable(itemId, message)
    is AudioEngineError.StreamFailed -> PlaybackError.StreamFailed(itemId, message)
    is AudioEngineError.EngineCrashed -> PlaybackError.EngineCrashed(itemId, message)
    AudioEngineError.SeekFailed -> PlaybackError.StreamFailed(itemId, "Seek failed")
}
