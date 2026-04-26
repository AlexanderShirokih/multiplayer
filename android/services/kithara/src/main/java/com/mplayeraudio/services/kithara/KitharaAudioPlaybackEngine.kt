package com.mplayeraudio.services.kithara

import com.kithara.KitharaError
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

internal class KitharaAudioPlaybackEngine(
    scope: CoroutineScope,
    player: KitharaPlayerHandle? = null,
) : AudioPlaybackEngine {
    // Internal collectors should not run directly in the caller scope.
    // shutdown() cancels childScope in tests, mirroring PlaybackQueueController.
    private val childScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val player: KitharaPlayerHandle = player ?: RealKitharaPlayerHandle(childScope)


    /** kitharaId → appItemId */
    private val itemIdMap = mutableMapOf<String, String>()
    private var currentItemHandle: KitharaItemHandle? = null
    private var itemObservationJob: Job? = null

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
        val seconds = positionMs.toDouble() / MsPerSecond
        return suspendCancellableCoroutine { continuation ->
            player.seek(seconds) { didFinish ->
                if (continuation.isActive) {
                    continuation.resume(didFinish)
                }
            }
        }
    }

    override fun loadTrack(request: AudioTrackRequest, autoPlay: Boolean) {
        cancelItemObservation()
        player.removeAllItems()
        itemIdMap.clear()

        val item = player.createAndLoadItem(request.url)
        currentItemHandle = item
        itemIdMap[item.kitharaId] = request.id
        _engineState.update { it.copy(currentItemId = request.id) }

        itemObservationJob = childScope.launch { observeItem(item, request.id, autoPlay) }
    }

    override fun stop() {
        cancelItemObservation()
        player.pause()
        player.removeAllItems()
        itemIdMap.clear()
        _engineState.value = AudioEngineState()
    }

    internal fun shutdown() {
        stop()
        childScope.cancel()
    }

    private fun cancelItemObservation() {
        itemObservationJob?.cancel()
        itemObservationJob = null
        currentItemHandle = null
    }

    private suspend fun observeItem(
        item: KitharaItemHandle,
        requestId: String,
        autoPlay: Boolean,
    ) {
        var playTriggered = false
        var failureEmitted = false
        var lastEmittedDurationMs: Long? = null

        item.snapshots.collect { snapshot ->
            val durationMs = snapshot.durationMs
            if (durationMs != null && durationMs != lastEmittedDurationMs) {
                lastEmittedDurationMs = durationMs
                _engineState.update { it.copy(durationMs = durationMs) }
                _events.emit(AudioEngineEvent.DurationDiscovered(requestId, durationMs))
            }

            when (snapshot.status) {
                EngineItemStatus.ReadyToPlay -> {
                    if (autoPlay && !playTriggered) {
                        playTriggered = true
                        player.play()
                    }
                }
                EngineItemStatus.Failed -> {
                    if (!failureEmitted) {
                        failureEmitted = true
                        val error = snapshot.error ?: AudioEngineError.LoadFailed("Unknown item error")
                        _events.emit(AudioEngineEvent.ItemFailed(requestId, error))
                    }
                }
                EngineItemStatus.Unknown -> Unit
            }
        }
    }

    private suspend fun collectPlayerState() {
        var engineFailedEmitted = false
        player.snapshots.collect { snapshot ->
            val currentAppItemId = snapshot.currentKitharaItemId?.let { itemIdMap[it] }

            _engineState.update { current ->
                current.copy(
                    status = snapshot.status,
                    currentPositionMs = snapshot.currentPositionMs,
                    bufferedPositionMs = snapshot.bufferedPositionMs,
                    currentItemId = currentAppItemId ?: current.currentItemId,
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
                    _events.emit(AudioEngineEvent.CurrentItemChanged(appItemId))
                }
                is EnginePlayerEvent.PlayedToEnd -> {
                    _events.emit(AudioEngineEvent.PlayedToEnd)
                }
            }
        }
    }
}

internal fun KitharaError?.toAudioEngineError(): AudioEngineError = when (this) {
    is KitharaError.ItemFailed -> AudioEngineError.LoadFailed(reason)
    is KitharaError.Internal -> AudioEngineError.EngineCrashed(description)
    is KitharaError.EngineNotRunning -> AudioEngineError.EngineCrashed("Engine not running")
    is KitharaError.InvalidArgument -> AudioEngineError.LoadFailed(reason)
    is KitharaError.NotReady -> AudioEngineError.LoadFailed("Engine not ready")
    is KitharaError.SeekFailed -> AudioEngineError.SeekFailed
    null -> AudioEngineError.EngineCrashed("Unknown error")
}

private const val MsPerSecond = 1_000.0
private const val EventBufferCapacity = 16
