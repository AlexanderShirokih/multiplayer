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

@Suppress("TooManyFunctions", "LargeClass")
internal class PlaybackQueueController(
    private val player: KitharaPlayerWrapper,
    urlResolver: PlayableUrlResolver,
    scope: CoroutineScope,
    private val loadTimeoutMs: Long = DefaultLoadTimeoutMs,
    private val logger: KitharaLogger = NoOpKitharaLogger,
) : PlaybackQueueBridge {

    private val cachingResolver = CachingPlayableUrlResolver(delegate = urlResolver)
    private val childScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))

    private val _state = MutableStateFlow(PlaybackQueueState())
    override val playbackState: StateFlow<PlaybackQueueState> = _state.asStateFlow()

    override val state: Flow<NowPlayingStripExternalState> = playbackState.map { queueState ->
        val currentItem = queueState.currentItem
        NowPlayingStripExternalState(
            title = currentItem?.title.orEmpty(),
            subtitle = currentItem?.subtitle.orEmpty(),
            isPlaying = queueState.isPlaying,
            currentPositionMs = queueState.currentPositionMs,
            durationMs = queueState.currentDurationMs ?: currentItem?.durationMs ?: 0L,
            controlsEnabled = queueState.controlsEnabled,
        )
    }

    private var failedCountSinceLastSuccess = 0
    private var watchdogJob: Job? = null
    private var currentHandle: KitharaItemHandle? = null
    private var loadedItemId: String? = null
    private var pendingTransition: PendingTransition? = null

    init {
        childScope.launch { collectPlayerSnapshots() }
        childScope.launch { collectPlayerEvents() }
    }

    override suspend fun replaceQueue(
        queue: List<PlaybackQueueItem>,
        startIndex: Int?,
        autoPlay: Boolean,
    ) {
        val previous = _state.value
        val preservedIndex = preservedIndex(previous, queue)
        val shouldPrepareItem = shouldPrepareItem(
            queue = queue,
            startIndex = startIndex,
            preservedIndex = preservedIndex,
            autoPlay = autoPlay,
        )
        val nextIndex = nextIndex(
            queue = queue,
            startIndex = startIndex,
            preservedIndex = preservedIndex,
            shouldPrepareItem = shouldPrepareItem,
        )
        val preservingCurrentTrack = nextIndex != null && nextIndex == preservedIndex
        val startPositionMs = startPosition(previous, queue, nextIndex, preservingCurrentTrack)
        val shouldPlay = shouldPlay(
            queue = queue,
            autoPlay = autoPlay,
            nextIndex = nextIndex,
            preservingCurrentTrack = preservingCurrentTrack,
            previous = previous,
        )

        _state.value = replacementState(
            previous = previous,
            queue = queue,
            nextIndex = nextIndex,
            preservingCurrentTrack = preservingCurrentTrack,
            startPositionMs = startPositionMs,
        )

        val action = when {
            queue.isEmpty() || !shouldPrepareItem -> QueueReplacementAction.ResetPlayer
            nextIndex == null -> QueueReplacementAction.None
            preservingCurrentTrack && loadedItemId == queue[nextIndex].id ->
                QueueReplacementAction.ResumePreserved(
                    shouldPlay = shouldPlay,
                    wasPlaying = previous.isPlaying,
                )

            else -> QueueReplacementAction.Load(
                index = nextIndex,
                autoPlay = shouldPlay,
                startPositionMs = startPositionMs,
            )
        }

        logger.trace(
            TAG,
            "replaceQueue action=$action queueSize=${queue.size} startIndex=$startIndex " +
                "autoPlay=$autoPlay preservedIndex=$preservedIndex nextIndex=$nextIndex " +
                "loadedItemId=$loadedItemId currentIndex=${previous.currentIndex} phase=${previous.phase}",
        )

        when (action) {
            QueueReplacementAction.None -> Unit
            QueueReplacementAction.ResetPlayer -> resetPlayer()
            is QueueReplacementAction.ResumePreserved -> {
                resumePreservedTrackIfNeeded(
                    shouldPlay = action.shouldPlay,
                    wasPlaying = action.wasPlaying,
                )
            }
            is QueueReplacementAction.Load -> {
                failedCountSinceLastSuccess = 0
                resetError()
                loadItem(
                    index = action.index,
                    autoPlay = action.autoPlay,
                    startPositionMs = action.startPositionMs,
                    reason = "replaceQueue",
                )
            }
        }
    }

    override suspend fun playTrack(index: Int) {
        if (index !in _state.value.queue.indices) return
        failedCountSinceLastSuccess = 0
        resetError()
        loadItem(
            index = index,
            autoPlay = true,
            startPositionMs = 0L,
            reason = "playTrack(index=$index)",
        )
    }

    override suspend fun play() {
        val current = _state.value
        if (current.queue.isNotEmpty()) {
            val index = current.currentIndex ?: 0
            val targetItemId = current.queue.getOrNull(index)?.id
            if (loadedItemId == targetItemId && targetItemId != null) {
                pendingTransition = null
                player.play()
            } else if (targetItemId != null) {
                failedCountSinceLastSuccess = 0
                resetError()
                loadItem(
                    index = index,
                    autoPlay = true,
                    startPositionMs = current.currentPositionMs,
                    reason = "play()",
                )
            }
        }
    }

    override suspend fun pause() {
        pendingTransition = null
        cancelWatchdog()
        player.pause()
        _state.update { current ->
            val nextPhase = when (current.phase) {
                PlaybackPhase.Loading,
                PlaybackPhase.Buffering,
                PlaybackPhase.Playing -> PlaybackPhase.Paused
                else -> current.phase
            }
            current.copy(phase = nextPhase)
        }
    }

    override suspend fun skipNext() {
        val current = _state.value
        val nextIndex = (current.currentIndex ?: -1) + 1
        if (nextIndex > current.queue.lastIndex) {
            markEnded()
            return
        }

        failedCountSinceLastSuccess = 0
        resetError()
        loadItem(
            index = nextIndex,
            autoPlay = true,
            startPositionMs = 0L,
            reason = "skipNext()",
        )
    }

    override suspend fun skipPrevious() {
        val current = _state.value
        val currentIndex = current.currentIndex ?: return
        if (current.currentPositionMs > RestartThresholdMs || currentIndex == 0) {
            seekTo(0L)
            return
        }

        failedCountSinceLastSuccess = 0
        resetError()
        loadItem(
            index = currentIndex - 1,
            autoPlay = true,
            startPositionMs = 0L,
            reason = "skipPrevious()",
        )
    }

    override suspend fun seekTo(positionMs: Long) {
        val current = _state.value
        val currentItem = current.currentItem ?: return
        val durationCap = current.currentDurationMs ?: currentItem.durationMs
        val clamped = positionMs.coerceIn(0L, durationCap.coerceAtLeast(0L))
        val wasPlaying = current.isPlaying

        pendingTransition = PendingTransition.Seek(
            itemId = currentItem.id,
            positionMs = clamped,
            wasPlaying = wasPlaying,
        )
        _state.update {
            it.copy(
                phase = when (it.phase) {
                    PlaybackPhase.Playing,
                    PlaybackPhase.Buffering -> PlaybackPhase.Buffering
                    else -> it.phase
                },
                playbackError = null,
            )
        }

        val didSeek = performPlayerSeek(clamped)
        if (!didSeek) {
            pendingTransition = null
            onItemFailed(currentItem.id, PlaybackError.StreamFailed(currentItem.id, "Seek failed"))
            return
        }

        pendingTransition = null
        _state.update {
            it.copy(
                phase = if (wasPlaying) PlaybackPhase.Playing else PlaybackPhase.Paused,
                currentPositionMs = clamped,
                playbackError = null,
            )
        }
    }

    override fun resetError() {
        _state.update { it.copy(playbackError = null) }
    }

    override fun shutdown() {
        childScope.cancel()
        resetPlayer()
        _state.value = PlaybackQueueState()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadItem(
        index: Int,
        autoPlay: Boolean,
        startPositionMs: Long,
        reason: String,
    ) {
        val item = _state.value.queue.getOrNull(index) ?: return
        logger.trace(
            TAG,
            "loadItem reason=$reason index=$index itemId=${item.id} autoPlay=$autoPlay " +
                "startPositionMs=$startPositionMs queueSize=${_state.value.queue.size}",
        )
        pendingTransition = PendingTransition.Load(
            itemId = item.id,
            autoPlay = autoPlay,
            initialSeekPositionMs = startPositionMs.takeIf { it > 0L },
        )

        _state.update {
            it.copy(
                currentIndex = index,
                phase = PlaybackPhase.Loading,
                currentPositionMs = startPositionMs.coerceAtLeast(0L),
                bufferedPositionMs = 0L,
                currentDurationMs = null,
                playbackError = null,
            )
        }

        if (autoPlay || startPositionMs > 0L) {
            startWatchdog(item.id)
        } else {
            cancelWatchdog()
        }

        try {
            player.pause()
            player.removeAllItems()
            clearCurrentHandle()

            val url = cachingResolver.getPlayableUrl(item)
            val handle = player.insertItem(url)
            currentHandle = handle
            loadedItemId = item.id
            player.selectItem(handle.kitharaId)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to load item ${item.id}", e)
            cancelWatchdog()
            val message = e.message?.takeUnless(String::isBlank) ?: "Load failed"
            onItemFailed(item.id, PlaybackError.TrackUnavailable(item.id, message))
        }
    }

    private suspend fun onCurrentItemReady(
        appItemId: String,
        durationMs: Long?,
    ) {
        if (loadedItemId != appItemId) return

        if (durationMs != null) {
            _state.update { current ->
                if (current.currentItem?.id == appItemId) {
                    current.copy(currentDurationMs = durationMs)
                } else {
                    current
                }
            }
        }
    }

    private suspend fun onCurrentItemSelected(appItemId: String) {
        val loadTransition = (pendingTransition as? PendingTransition.Load)
            ?.takeIf { it.itemId == appItemId }
            ?: return

        val initialSeekPositionMs = loadTransition.initialSeekPositionMs
        if (initialSeekPositionMs != null) {
            pendingTransition = PendingTransition.Seek(
                itemId = appItemId,
                positionMs = initialSeekPositionMs,
                wasPlaying = loadTransition.autoPlay,
            )
            val didSeek = performPlayerSeek(initialSeekPositionMs)
            if (!didSeek) {
                pendingTransition = null
                onItemFailed(appItemId, PlaybackError.StreamFailed(appItemId, "Seek failed"))
                return
            }

            _state.update { current ->
                if (current.currentItem?.id == appItemId) {
                    current.copy(currentPositionMs = initialSeekPositionMs)
                } else {
                    current
                }
            }

            if (loadTransition.autoPlay) {
                pendingTransition = loadTransition.copy(
                    initialSeekPositionMs = null,
                    playRequested = true,
                )
                player.play()
                confirmPlaybackFromLatestSnapshotIfRunning()
            } else {
                pendingTransition = null
                cancelWatchdog()
                _state.update { current ->
                    if (current.currentItem?.id == appItemId) {
                        current.copy(phase = PlaybackPhase.Paused)
                    } else {
                        current
                    }
                }
            }
        } else if (loadTransition.autoPlay) {
            if (!loadTransition.playRequested) {
                pendingTransition = loadTransition.copy(playRequested = true)
                player.play()
                confirmPlaybackFromLatestSnapshotIfRunning()
            }
        } else {
            pendingTransition = null
            cancelWatchdog()
            _state.update { current ->
                if (current.currentItem?.id == appItemId) {
                    current.copy(phase = PlaybackPhase.Paused)
                } else {
                    current
                }
            }
        }
    }

    private suspend fun collectPlayerSnapshots() {
        player.snapshots.collect { snapshot ->
            if (snapshot.status == AudioEngineStatus.Failed) {
                val itemId = _state.value.currentItem?.id
                    ?: pendingTransition?.itemId
                    ?: loadedItemId
                    ?: return@collect
                val error = snapshot.error ?: AudioEngineError.EngineCrashed("Unknown engine error")
                onItemFailed(itemId, error.toPlaybackError(itemId))
                return@collect
            }

            val transition = pendingTransition
            val snapshotMatchesCurrentItem = snapshot.kitharaItemIdMatches(currentHandle)
            val confirmedPlayback = snapshot.confirmsCurrentPlayback(
                handle = currentHandle,
                transition = transition,
            )
            if (confirmedPlayback) {
                failedCountSinceLastSuccess = 0
                pendingTransition = null
                cancelWatchdog()
            }

            _state.update { current ->
                current.copy(
                    phase = resolvePhase(
                        currentPhase = current.phase,
                        playerStatus = snapshot.status,
                        isPlaying = confirmedPlayback,
                        pendingTransition = pendingTransition,
                    ),
                    currentPositionMs = (pendingTransition as? PendingTransition.Seek)?.positionMs
                        ?: current.currentPositionMs.takeIf { transition is PendingTransition.Load }
                        ?: snapshot.currentPositionMs.takeIf { snapshotMatchesCurrentItem }
                        ?: current.currentPositionMs,
                    bufferedPositionMs = current.bufferedPositionMs.takeIf { transition is PendingTransition.Load }
                        ?: snapshot.bufferedPositionMs.takeIf { snapshotMatchesCurrentItem }
                        ?: current.bufferedPositionMs,
                )
            }
        }
    }

    private suspend fun collectPlayerEvents() {
        player.events.collect(::handlePlayerEvent)
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun handlePlayerEvent(event: KitharaPlayerEvent) {
        when (event) {
            is KitharaPlayerEvent.CurrentItemChanged -> {
                if (event.kitharaItemId == null || event.kitharaItemId == currentHandle?.kitharaId) {
                    if (pendingTransition is PendingTransition.Seek) {
                        pendingTransition = null
                    }
                    _state.update { it.copy(currentPositionMs = 0L, bufferedPositionMs = 0L, playbackError = null) }
                    if (event.kitharaItemId == currentHandle?.kitharaId) {
                        currentLoadedAppItemId()?.let { appItemId ->
                            onCurrentItemSelected(appItemId)
                        }
                    }
                }
            }
            is KitharaPlayerEvent.PlayedToEnd -> {
                if (event.kitharaItemId == currentHandle?.kitharaId) {
                    onCurrentItemEnded()
                }
            }
            is KitharaPlayerEvent.ItemReady -> {
                if (event.kitharaItemId == currentHandle?.kitharaId) {
                    currentLoadedAppItemId()?.let { appItemId ->
                        onCurrentItemReady(appItemId = appItemId, durationMs = event.durationMs)
                    }
                }
            }
            is KitharaPlayerEvent.ItemFailed -> {
                if (event.kitharaItemId == currentHandle?.kitharaId) {
                    currentLoadedAppItemId()?.let { appItemId ->
                        onItemFailed(appItemId, event.error.toPlaybackError(appItemId))
                    }
                }
            }
            is KitharaPlayerEvent.DurationDiscovered -> {
                if (event.kitharaItemId == currentHandle?.kitharaId) {
                    val appItemId = currentLoadedAppItemId() ?: return
                    _state.update { current ->
                        if (current.currentItem?.id == appItemId) {
                            current.copy(currentDurationMs = event.durationMs)
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    private fun onCurrentItemEnded() {
        val currentIndex = _state.value.currentIndex ?: return
        val nextIndex = currentIndex + 1
        if (nextIndex > _state.value.queue.lastIndex) {
            markEnded()
            return
        }

        childScope.launch {
            failedCountSinceLastSuccess = 0
            resetError()
            logger.trace(
                TAG,
                "onCurrentItemEnded currentIndex=$currentIndex nextIndex=$nextIndex " +
                    "currentItemId=${_state.value.currentItem?.id}",
            )
            loadItem(
                index = nextIndex,
                autoPlay = true,
                startPositionMs = 0L,
                reason = "playedToEnd(currentIndex=$currentIndex)",
            )
        }
    }

    private fun currentLoadedAppItemId(): String? {
        return loadedItemId ?: _state.value.currentItem?.id
    }

    private fun onItemFailed(
        itemId: String,
        error: PlaybackError,
    ) {
        val current = _state.value
        val failedIndex = current.queue.indexOfFirst { it.id == itemId }.takeIf { it >= 0 }
        val effectiveIndex = failedIndex ?: current.currentIndex
        if (effectiveIndex == null) return

        cancelWatchdog()
        pendingTransition = null
        failedCountSinceLastSuccess++

        _state.update {
            it.copy(
                currentIndex = effectiveIndex,
                phase = PlaybackPhase.Failed,
                playbackError = error,
            )
        }

        val nextIndex = effectiveIndex + 1
        if (nextIndex <= current.queue.lastIndex && failedCountSinceLastSuccess < current.queue.size) {
            childScope.launch {
                delay(AutoSkipDelayMs)
                if (_state.value.phase == PlaybackPhase.Failed) {
                    _state.update { it.copy(playbackError = null) }
                    loadItem(
                        index = nextIndex,
                        autoPlay = true,
                        startPositionMs = 0L,
                        reason = "autoSkipAfterFailure(itemId=$itemId)",
                    )
                }
            }
        }
    }

    private fun markEnded() {
        cancelWatchdog()
        pendingTransition = null
        _state.update { it.copy(phase = PlaybackPhase.Ended, currentPositionMs = 0L) }
    }

    private fun resolvePhase(
        currentPhase: PlaybackPhase,
        playerStatus: AudioEngineStatus,
        isPlaying: Boolean,
        pendingTransition: PendingTransition?,
    ): PlaybackPhase {
        return when {
            pendingTransition is PendingTransition.Seek && currentPhase == PlaybackPhase.Paused -> PlaybackPhase.Paused
            pendingTransition is PendingTransition.Seek -> PlaybackPhase.Buffering
            isPlaying -> PlaybackPhase.Playing
            pendingTransition is PendingTransition.Load -> PlaybackPhase.Loading
            playerStatus == AudioEngineStatus.ReadyToPlay && loadedItemId != null -> PlaybackPhase.Paused
            else -> currentPhase
        }
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

    private fun confirmPlaybackFromLatestSnapshotIfRunning() {
        val snapshot = player.snapshots.value
        if (!snapshot.kitharaItemIdMatches(currentHandle) || snapshot.rate <= 0f) return

        failedCountSinceLastSuccess = 0
        pendingTransition = null
        cancelWatchdog()
        _state.update { current ->
            current.copy(
                phase = PlaybackPhase.Playing,
                currentPositionMs = snapshot.currentPositionMs,
                bufferedPositionMs = snapshot.bufferedPositionMs,
            )
        }
    }

    private fun resetPlayer() {
        cancelWatchdog()
        pendingTransition = null
        player.pause()
        player.removeAllItems()
        clearCurrentHandle()
        loadedItemId = null
    }

    private fun resumePreservedTrackIfNeeded(
        shouldPlay: Boolean,
        wasPlaying: Boolean,
    ) {
        cancelWatchdog()
        pendingTransition = null
        if (shouldPlay && !wasPlaying) {
            player.play()
            _state.update { it.copy(phase = PlaybackPhase.Playing) }
        }
    }

    private fun clearCurrentHandle() {
        currentHandle = null
    }

    private fun preservedIndex(
        previous: PlaybackQueueState,
        queue: List<PlaybackQueueItem>,
    ): Int? {
        return previous.currentItem
            ?.let { currentItem -> queue.indexOfFirst { it.id == currentItem.id }.takeIf { it >= 0 } }
    }

    private fun nextIndex(
        queue: List<PlaybackQueueItem>,
        startIndex: Int?,
        preservedIndex: Int?,
        shouldPrepareItem: Boolean,
    ): Int? {
        if (!shouldPrepareItem) return null
        return startIndex ?: preservedIndex ?: queue.indices.firstOrNull()
    }

    private fun shouldPrepareItem(
        queue: List<PlaybackQueueItem>,
        startIndex: Int?,
        preservedIndex: Int?,
        autoPlay: Boolean,
    ): Boolean {
        return queue.isNotEmpty() && (autoPlay || startIndex != null || preservedIndex != null)
    }

    private fun startPosition(
        previous: PlaybackQueueState,
        queue: List<PlaybackQueueItem>,
        nextIndex: Int?,
        preservingCurrentTrack: Boolean,
    ): Long {
        if (!preservingCurrentTrack || nextIndex == null) return 0L
        return previous.currentPositionMs.coerceIn(0L, queue[nextIndex].durationMs.coerceAtLeast(0L))
    }

    private fun shouldPlay(
        queue: List<PlaybackQueueItem>,
        autoPlay: Boolean,
        nextIndex: Int?,
        preservingCurrentTrack: Boolean,
        previous: PlaybackQueueState,
    ): Boolean {
        return when {
            queue.isEmpty() -> false
            autoPlay && nextIndex != null -> true
            preservingCurrentTrack -> previous.isPlaying
            else -> false
        }
    }

    private fun replacementState(
        previous: PlaybackQueueState,
        queue: List<PlaybackQueueItem>,
        nextIndex: Int?,
        preservingCurrentTrack: Boolean,
        startPositionMs: Long,
    ): PlaybackQueueState {
        return PlaybackQueueState(
            queue = queue,
            currentIndex = nextIndex,
            phase = if (queue.isEmpty()) {
                PlaybackPhase.Idle
            } else if (preservingCurrentTrack) {
                previous.phase
            } else {
                PlaybackPhase.Idle
            },
            currentPositionMs = if (preservingCurrentTrack) startPositionMs else 0L,
            currentDurationMs = if (preservingCurrentTrack) previous.currentDurationMs else null,
        )
    }

    private suspend fun performPlayerSeek(positionMs: Long): Boolean {
        return player.seek(seconds = positionMs / 1000.0)
    }

    private companion object {
        const val TAG = "PlaybackQueueController"
        const val DefaultLoadTimeoutMs = 30_000L
        const val RestartThresholdMs = 3_000L
        const val AutoSkipDelayMs = 100L
    }
}

private fun AudioEngineError.toPlaybackError(itemId: String): PlaybackError = when (this) {
    is AudioEngineError.LoadFailed -> PlaybackError.TrackUnavailable(itemId, message)
    is AudioEngineError.StreamFailed -> PlaybackError.StreamFailed(itemId, message)
    is AudioEngineError.EngineCrashed -> PlaybackError.EngineCrashed(itemId, message)
    AudioEngineError.SeekFailed -> PlaybackError.StreamFailed(itemId, "Seek failed")
}

private fun EnginePlayerSnapshot.kitharaItemIdMatches(handle: KitharaItemHandle?): Boolean {
    return currentKitharaItemId != null && currentKitharaItemId == handle?.kitharaId
}

private fun EnginePlayerSnapshot.confirmsCurrentPlayback(
    handle: KitharaItemHandle?,
    transition: PendingTransition?,
): Boolean {
    if (!kitharaItemIdMatches(handle) || rate <= 0f) return false
    return when (transition) {
        is PendingTransition.Load -> transition.playRequested
        else -> true
    }
}

private sealed interface PendingTransition {
    val itemId: String

    data class Load(
        override val itemId: String,
        val autoPlay: Boolean,
        val initialSeekPositionMs: Long?,
        val playRequested: Boolean = false,
    ) : PendingTransition

    data class Seek(
        override val itemId: String,
        val positionMs: Long,
        val wasPlaying: Boolean,
    ) : PendingTransition
}

private sealed interface QueueReplacementAction {
    data object None : QueueReplacementAction
    data object ResetPlayer : QueueReplacementAction

    data class ResumePreserved(
        val shouldPlay: Boolean,
        val wasPlaying: Boolean,
    ) : QueueReplacementAction

    data class Load(
        val index: Int,
        val autoPlay: Boolean,
        val startPositionMs: Long,
    ) : QueueReplacementAction
}
