package com.mplayeraudio.services.kithara

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

@Suppress("TooManyFunctions")
internal class KitharaAudioPlaybackEngine(
    scope: CoroutineScope,
    player: KitharaPlayerHandle? = null,
    private val logger: KitharaLogger = NoOpKitharaLogger,
) : AudioPlaybackEngine {
    private val childScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val player: KitharaPlayerHandle = player ?: RealKitharaPlayerHandle(childScope)

    /** kitharaId → appItemId */
    private val itemIdMap = mutableMapOf<String, String>()
    private val windowHandles = LinkedHashMap<String, KitharaItemHandle>()
    private val itemObservationJobs = mutableMapOf<String, Job>()
    private var pendingAutoPlayAppItemId: String? = null

    private val _engineState = MutableStateFlow(AudioEngineState())
    override val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    private val _events = MutableSharedFlow<AudioEngineEvent>(extraBufferCapacity = EventBufferCapacity)
    override val events: SharedFlow<AudioEngineEvent> = _events.asSharedFlow()

    init {
        childScope.launch { collectPlayerState() }
        childScope.launch { collectPlayerEvents() }
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override suspend fun seekTo(positionMs: Long): Boolean {
        val seconds = positionMs.milliseconds.toDouble(DurationUnit.SECONDS)
        return suspendCancellableCoroutine { continuation ->
            player.seek(seconds) { didFinish ->
                if (continuation.isActive) {
                    continuation.resume(didFinish)
                }
            }
        }
    }

    override fun setQueueWindow(
        current: AudioTrackRequest,
        next: AudioTrackRequest?,
        autoPlay: Boolean,
    ) {
        cancelAllItemObservations()
        player.pause()
        player.removeAllItems()
        itemIdMap.clear()
        windowHandles.clear()
        pendingAutoPlayAppItemId = current.id.takeIf { autoPlay }
        _engineState.value = AudioEngineState(currentItemId = current.id)

        insertWindowItem(current)
        next?.let(::insertWindowItem)
    }

    override fun appendNext(next: AudioTrackRequest) {
        if (windowHandles.containsKey(next.id)) return
        insertWindowItem(next)
    }

    override suspend fun selectInWindow(appItemId: String, autoPlay: Boolean): Boolean {
        val item = windowHandles[appItemId] ?: return false

        player.selectItem(item.kitharaId)
        _engineState.update {
            it.copy(
                currentItemId = appItemId,
                currentPositionMs = 0L,
                bufferedPositionMs = 0L,
                durationMs = item.snapshots.value.durationMs,
                error = null,
            )
        }

        if (autoPlay && item.snapshots.value.status == EngineItemStatus.ReadyToPlay) {
            pendingAutoPlayAppItemId = null
            player.play()
        } else {
            pendingAutoPlayAppItemId = appItemId.takeIf { autoPlay }
        }

        return true
    }

    override fun pruneWindow(keepAppItemIds: Set<String>) {
        windowHandles.keys
            .filterNot(keepAppItemIds::contains)
            .toList()
            .forEach(::removeWindowItem)
    }

    override fun stop() {
        cancelAllItemObservations()
        player.pause()
        player.removeAllItems()
        itemIdMap.clear()
        windowHandles.clear()
        pendingAutoPlayAppItemId = null
        _engineState.value = AudioEngineState()
    }

    internal fun shutdown() {
        stop()
        childScope.cancel()
    }

    private fun cancelAllItemObservations() {
        itemObservationJobs.values.forEach(Job::cancel)
        itemObservationJobs.clear()
    }

    private fun insertWindowItem(request: AudioTrackRequest) {
        if (windowHandles.containsKey(request.id)) return

        val item = player.insertItem(request.url)
        windowHandles[request.id] = item
        itemIdMap[item.kitharaId] = request.id
        itemObservationJobs[request.id] = childScope.launch { observeItem(item, request.id) }
    }

    private fun removeWindowItem(appItemId: String) {
        val item = windowHandles.remove(appItemId) ?: return
        itemObservationJobs.remove(appItemId)?.cancel()
        itemIdMap.remove(item.kitharaId)
        if (pendingAutoPlayAppItemId == appItemId) {
            pendingAutoPlayAppItemId = null
        }
        player.removeItem(item.kitharaId)
    }

    private suspend fun observeItem(
        item: KitharaItemHandle,
        requestId: String,
    ) {
        var failureHandled = false
        var lastEmittedDurationMs: Long? = null

        item.snapshots.collect { snapshot ->
            val durationMs = snapshot.durationMs
            if (durationMs != null && durationMs != lastEmittedDurationMs) {
                lastEmittedDurationMs = durationMs
                if (_engineState.value.currentItemId == requestId) {
                    _engineState.update { it.copy(durationMs = durationMs) }
                }
                _events.emit(AudioEngineEvent.DurationDiscovered(requestId, durationMs))
            }

            when (snapshot.status) {
                EngineItemStatus.ReadyToPlay -> {
                    if (pendingAutoPlayAppItemId == requestId) {
                        pendingAutoPlayAppItemId = null
                        player.play()
                    }
                }
                EngineItemStatus.Failed -> {
                    if (failureHandled) return@collect
                    failureHandled = true

                    if (_engineState.value.currentItemId == requestId) {
                        val error = snapshot.error ?: AudioEngineError.LoadFailed("Unknown item error")
                        _events.emit(AudioEngineEvent.ItemFailed(requestId, error))
                    } else {
                        logger.trace(TAG, "Dropping failed preloaded item: $requestId")
                        childScope.launch { removeWindowItem(requestId) }
                    }
                }
                EngineItemStatus.Unknown -> Unit
            }
        }
    }

    private suspend fun collectPlayerState() {
        var engineFailedEmitted = false
        player.snapshots.collect { snapshot ->
            _engineState.update { current ->
                current.copy(
                    status = snapshot.status,
                    currentPositionMs = snapshot.currentPositionMs,
                    bufferedPositionMs = snapshot.bufferedPositionMs,
                    currentItemId = snapshot.currentKitharaItemId?.let(itemIdMap::get) ?: current.currentItemId,
                    isPlaying = snapshot.rate > 0f,
                    error = snapshot.error,
                )
            }

            if (snapshot.status == AudioEngineStatus.Failed && !engineFailedEmitted) {
                engineFailedEmitted = true
                val error = snapshot.error ?: AudioEngineError.EngineCrashed("Unknown engine error")
                _events.emit(AudioEngineEvent.EngineFailed(error))
            }
            if (snapshot.status != AudioEngineStatus.Failed) {
                engineFailedEmitted = false
            }
        }
    }

    private suspend fun collectPlayerEvents() {
        player.events.collect { event ->
            when (event) {
                is EnginePlayerEvent.CurrentItemChanged -> {
                    val appItemId = event.kitharaItemId?.let { itemIdMap[it] }
                    _engineState.update {
                        it.copy(
                            currentItemId = appItemId,
                            currentPositionMs = 0L,
                            durationMs = appItemId?.let(windowHandles::get)?.snapshots?.value?.durationMs,
                            error = null,
                        )
                    }
                    _events.emit(AudioEngineEvent.CurrentItemChanged(appItemId))
                }
                is EnginePlayerEvent.PlayedToEnd -> {
                    val appItemId = itemIdMap[event.kitharaItemId] ?: return@collect
                    _events.emit(AudioEngineEvent.PlayedToEnd(appItemId))
                }
            }
        }
    }

    private companion object {
        const val TAG = "KitharaPlaybackEngine"
    }
}

private const val EventBufferCapacity = 16
