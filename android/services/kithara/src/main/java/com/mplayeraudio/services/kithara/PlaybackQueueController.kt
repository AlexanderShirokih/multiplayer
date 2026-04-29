package com.mplayeraudio.services.kithara

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
    private val logger: KitharaLogger = NoOpKitharaLogger,
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
            ?.let { currentItem -> queue.indexOfFirst { it.id == currentItem.id }.takeIf { it >= 0 } }
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
            phase = when {
                queue.isEmpty() -> PlaybackPhase.Idle
                plan.preservingCurrentTrack -> previous.phase
                plan.shouldPlay -> PlaybackPhase.Loading
                else -> PlaybackPhase.Idle
            },
            currentPositionMs = plan.startPositionMs,
            currentDurationMs = if (plan.preservingCurrentTrack) previous.currentDurationMs else null,
        )

        if (queue.isEmpty()) {
            cancelWatchdog()
            updateTicker(isPlaying = false)
            engine.stop()
        } else {
            plan.nextIndex?.let { nextIndex ->
                failedCountSinceLastSuccess = 0

                if (plan.preservingCurrentTrack) {
                    cancelWatchdog()
                    syncPreservedWindow(previous = previous, nextIndex = nextIndex)

                    if (plan.shouldPlay && !previous.isPlaying) {
                        engine.play()
                        _state.update { it.copy(phase = PlaybackPhase.Playing) }
                        updateTicker(isPlaying = true)
                    } else {
                        updateTicker(isPlaying = previous.isPlaying)
                    }
                } else {
                    applyWindow(
                        currentIndex = nextIndex,
                        autoPlay = plan.shouldPlay,
                        startPositionMs = plan.startPositionMs,
                    )
                }
            }
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

        selectOrApplyWindow(index)
    }

    override suspend fun play() {
        val current = _state.value
        if (current.queue.isEmpty()) return

        val index = current.currentIndex ?: 0
        val loadedItemId = engine.engineState.value.currentItemId
        val currentItemId = current.queue.getOrNull(index)?.id

        if (loadedItemId == currentItemId && loadedItemId != null) {
            engine.play()
            _state.update { it.copy(phase = PlaybackPhase.Playing, playbackError = null) }
            updateTicker(isPlaying = true)
            return
        }

        failedCountSinceLastSuccess = 0
        _state.update {
            it.copy(
                currentIndex = index,
                phase = PlaybackPhase.Loading,
                currentDurationMs = null,
                playbackError = null,
            )
        }
        applyWindow(index, autoPlay = true, startPositionMs = current.currentPositionMs)
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

        selectOrApplyWindow(nextIndex)
    }

    override suspend fun skipPrevious() {
        val current = _state.value
        val currentIndex = current.currentIndex ?: return
        if (current.currentPositionMs > RestartThresholdMs || currentIndex == 0) {
            engine.seekTo(0L)
            _state.update { it.copy(currentPositionMs = 0L) }
            return
        }

        val previousIndex = currentIndex - 1
        failedCountSinceLastSuccess = 0
        _state.update {
            it.copy(
                currentIndex = previousIndex,
                phase = PlaybackPhase.Loading,
                currentPositionMs = 0L,
                currentDurationMs = null,
                playbackError = null,
            )
        }

        selectOrApplyWindow(previousIndex)
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
    private suspend fun applyWindow(
        currentIndex: Int,
        autoPlay: Boolean,
        startPositionMs: Long,
    ) {
        val queue = _state.value.queue
        val currentItem = queue.getOrNull(currentIndex) ?: return
        val nextItem = queue.getOrNull(currentIndex + 1)

        if (autoPlay) {
            startWatchdog(currentItem.id)
        } else {
            cancelWatchdog()
        }

        try {
            val currentUrl = cachingResolver.getPlayableUrl(currentItem)
            val nextRequest = nextItem?.let { item ->
                AudioTrackRequest(id = item.id, url = cachingResolver.getPlayableUrl(item))
            }

            engine.setQueueWindow(
                current = AudioTrackRequest(id = currentItem.id, url = currentUrl),
                next = nextRequest,
                autoPlay = autoPlay,
            )

            if (startPositionMs > 0L) {
                engine.seekTo(startPositionMs)
            }
        } catch (e: Exception) {
            logger.error(TAG, "Failed to apply queue window for ${currentItem.id}", e)
            cancelWatchdog()
            val message = e.message?.takeUnless(String::isBlank) ?: "Load failed"
            onItemFailed(currentItem.id, PlaybackError.TrackUnavailable(currentItem.id, message))
        }
    }

    private suspend fun selectOrApplyWindow(index: Int) {
        val item = _state.value.queue.getOrNull(index) ?: return
        if (engine.selectInWindow(item.id, autoPlay = true)) {
            cancelWatchdog()
            return
        }

        applyWindow(currentIndex = index, autoPlay = true, startPositionMs = 0L)
    }

    private suspend fun syncPreservedWindow(
        previous: PlaybackQueueState,
        nextIndex: Int,
    ) {
        val queue = _state.value.queue
        engine.pruneWindow(keepAppItemIds(queue, nextIndex))

        val previousNextId = previous.currentIndex
            ?.plus(1)
            ?.let(previous.queue::getOrNull)
            ?.id
        val nextItem = queue.getOrNull(nextIndex + 1) ?: return
        if (nextItem.id == previousNextId) return

        val url = cachingResolver.getPlayableUrl(nextItem)
        engine.appendNext(AudioTrackRequest(id = nextItem.id, url = url))
    }

    private suspend fun extendWindowIfNeeded() {
        val current = _state.value
        val currentIndex = current.currentIndex ?: return
        val queue = current.queue

        engine.pruneWindow(keepAppItemIds(queue, currentIndex))

        val nextItem = queue.getOrNull(currentIndex + 1) ?: return
        val url = cachingResolver.getPlayableUrl(nextItem)
        engine.appendNext(AudioTrackRequest(id = nextItem.id, url = url))
    }

    private suspend fun collectEngineState() {
        engine.engineState.collect { engineState ->
            _state.update { current ->
                val isForCurrentItem = engineState.currentItemId != null &&
                    engineState.currentItemId == current.currentItem?.id
                if (!isForCurrentItem) return@update current

                val newPhase = when {
                    current.phase == PlaybackPhase.Failed ||
                        current.phase == PlaybackPhase.Idle ||
                        current.phase == PlaybackPhase.Ended -> current.phase
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
                is AudioEngineEvent.CurrentItemChanged -> onCurrentItemChanged(event.itemId)
                is AudioEngineEvent.ItemFailed ->
                    onEngineItemFailed(event.itemId, event.reason.toPlaybackError(event.itemId))
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
            }
        }
    }

    private fun onPlayedToEnd(itemId: String) {
        val current = _state.value
        val currentItem = current.currentItem ?: return
        if (itemId != currentItem.id || current.currentIndex != current.queue.lastIndex) return
        markEnded()
    }

    private suspend fun onCurrentItemChanged(appItemId: String?) {
        if (appItemId == null) return

        val nextIndex = _state.value.queue.indexOfFirst { it.id == appItemId }
        if (nextIndex < 0) return

        failedCountSinceLastSuccess = 0
        cancelWatchdog()
        _state.update { current ->
            current.copy(
                currentIndex = nextIndex,
                phase = if (engine.engineState.value.isPlaying) PlaybackPhase.Playing else current.phase,
                currentPositionMs = 0L,
                currentDurationMs = null,
                playbackError = null,
            )
        }
        updateTicker(isPlaying = engine.engineState.value.isPlaying)
        extendWindowIfNeeded()
    }

    private fun onEngineItemFailed(itemId: String, error: PlaybackError) {
        if (itemId == _state.value.currentItem?.id) {
            onItemFailed(itemId, error)
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
                            playbackError = null,
                        )
                    }
                    selectOrApplyWindow(nextIndex)
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

    private fun keepAppItemIds(
        queue: List<PlaybackQueueItem>,
        currentIndex: Int,
    ): Set<String> = buildSet {
        queue.getOrNull(currentIndex)?.let { add(it.id) }
        queue.getOrNull(currentIndex + 1)?.let { add(it.id) }
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
