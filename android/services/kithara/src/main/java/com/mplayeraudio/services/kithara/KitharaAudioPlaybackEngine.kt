package com.mplayeraudio.services.kithara

import com.kithara.ItemStatus
import com.kithara.KitharaPlayer
import com.kithara.KitharaPlayerEvent
import com.kithara.KitharaPlayerItem
import com.kithara.PlayerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class KitharaAudioPlaybackEngine(
    private val scope: CoroutineScope,
) : AudioPlaybackEngine {

    private val player = KitharaPlayer()

    private val itemIdMap = mutableMapOf<String, String>()

    private val _engineState = MutableStateFlow(AudioEngineState())
    override val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()

    private val _events = MutableSharedFlow<AudioEngineEvent>(extraBufferCapacity = EventBufferCapacity)
    override val events: SharedFlow<AudioEngineEvent> = _events.asSharedFlow()

    init {
        scope.launch { collectPlayerState() }
        scope.launch { collectPlayerEvents() }
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

    override fun loadTrack(request: AudioTrackRequest) {
        player.removeAllItems()
        itemIdMap.clear()

        val item = KitharaPlayerItem(url = request.url)
        itemIdMap[item.id] = request.id
        item.load()
        player.insert(item)

        scope.launch { awaitItemReadyAndPlay(item, request.id) }
    }

    override fun stop() {
        player.pause()
        player.removeAllItems()
        itemIdMap.clear()
        _engineState.value = AudioEngineState()
    }

    private suspend fun awaitItemReadyAndPlay(item: KitharaPlayerItem, requestId: String) {
        val itemState = item.state.first { state -> state.status != ItemStatus.Unknown }
        when (itemState.status) {
            ItemStatus.ReadyToPlay -> player.play()
            ItemStatus.Failed -> {
                val reason = itemState.error?.message ?: "Unknown item error"
                _events.tryEmit(AudioEngineEvent.ItemFailed(requestId, reason))
            }
            ItemStatus.Unknown -> Unit
        }
    }

    private suspend fun collectPlayerState() {
        player.state.collect { playerState ->
            val currentKitharaItemId = playerState.items.firstOrNull()?.id
            val currentAppItemId = currentKitharaItemId?.let { itemIdMap[it] }

            _engineState.value = AudioEngineState(
                status = playerState.status.toEngineStatus(),
                currentPositionMs = (playerState.currentTime * MsPerSecond).toLong(),
                durationMs = playerState.duration?.let { (it * MsPerSecond).toLong() },
                currentItemId = currentAppItemId,
                isPlaying = playerState.rate > 0f,
                error = playerState.error,
            )
        }
    }

    private suspend fun collectPlayerEvents() {
        player.events.collect { event ->
            when (event) {
                is KitharaPlayerEvent.CurrentItemChanged -> {
                    val appItemId = event.itemId?.let { itemIdMap[it] }
                    _events.emit(AudioEngineEvent.CurrentItemChanged(appItemId))
                }
                is KitharaPlayerEvent.PlayedToEnd -> {
                    _events.emit(AudioEngineEvent.PlayedToEnd)
                }
            }
        }
    }
}

private fun PlayerStatus.toEngineStatus(): AudioEngineStatus = when (this) {
    PlayerStatus.Unknown -> AudioEngineStatus.Idle
    PlayerStatus.ReadyToPlay -> AudioEngineStatus.ReadyToPlay
    PlayerStatus.Failed -> AudioEngineStatus.Failed
}

private const val MsPerSecond = 1_000.0
private const val EventBufferCapacity = 16
