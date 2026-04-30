package com.mplayeraudio.services.kithara

import com.kithara.GaplessMode
import com.kithara.ItemStatus
import com.kithara.KitharaError
import com.kithara.KitharaPlayer
import com.kithara.KitharaPlayerItem
import com.kithara.PlayerStatus
import com.kithara.SilenceTrimParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Снапшот состояния отдельного item'а, отображённый из Kithara-типов.
 * Содержит только наши типы — без зависимости на com.kithara в интерфейсе.
 */
internal data class EngineItemSnapshot(
    val status: EngineItemStatus = EngineItemStatus.Unknown,
    val durationMs: Long? = null,
    val error: AudioEngineError? = null,
)

internal enum class EngineItemStatus { Unknown, ReadyToPlay, Failed }

/**
 * Снапшот состояния плеера в целом, отображённый из Kithara-типов.
 */
internal data class EnginePlayerSnapshot(
    val status: AudioEngineStatus = AudioEngineStatus.Idle,
    val currentPositionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val rate: Float = 0f,
    val error: AudioEngineError? = null,
    val currentKitharaItemId: String? = null,
)

/** High-level события плеера и item'ов без утечки FFI-типов. */
internal sealed interface KitharaPlayerEvent {
    data class CurrentItemChanged(val kitharaItemId: String?) : KitharaPlayerEvent
    data class PlayedToEnd(val kitharaItemId: String) : KitharaPlayerEvent
    data class ItemReady(val kitharaItemId: String, val durationMs: Long?) : KitharaPlayerEvent
    data class ItemFailed(val kitharaItemId: String, val error: AudioEngineError) :
        KitharaPlayerEvent

    data class DurationDiscovered(val kitharaItemId: String, val durationMs: Long) :
        KitharaPlayerEvent
}

/**
 * Дескриптор конкретного item'а, загруженного в [KitharaPlayerWrapper].
 * Интерфейс использует только наши типы — тесты не зависят от Kithara classpath.
 */
internal interface KitharaItemHandle {
    val kitharaId: String
}

internal interface KitharaPlayerDriver {
    val state: StateFlow<com.kithara.PlayerState>
    val events: Flow<com.kithara.KitharaPlayerEvent>

    fun play()
    fun pause()
    suspend fun seek(seconds: Double): Boolean
    fun createItem(url: String): KitharaPlayerItem
    fun load(item: KitharaPlayerItem)
    fun insert(item: KitharaPlayerItem)
    fun selectItem(index: Int, autoplay: Boolean)
    fun remove(item: KitharaPlayerItem)
    fun removeAllItems()
}

/**
 * Обёртка над [KitharaPlayer] для изоляции FFI-вызовов.
 * Публичная поверхность не содержит com.kithara.* типов — позволяет
 * изолировать FFI и подменять нижний слой в unit-тестах.
 */
@Suppress("TooManyFunctions")
internal class KitharaPlayerWrapper(
    private val scope: CoroutineScope,
    private val player: KitharaPlayerDriver = RealKitharaPlayerDriver(),
) {

    private val insertedItems = LinkedHashMap<String, KitharaPlayerItem>()
    private val itemObservationJobs = LinkedHashMap<String, Job>()
    private val currentItemId = MutableStateFlow<String?>(null)
    private val _events = MutableSharedFlow<KitharaPlayerEvent>(extraBufferCapacity = 16)

    val snapshots: StateFlow<EnginePlayerSnapshot> = combine(
        player.state,
        currentItemId.asStateFlow(),
    ) { ps, selectedKitharaItemId ->
        EnginePlayerSnapshot(
            status = when (ps.status) {
                PlayerStatus.Unknown -> AudioEngineStatus.Idle
                PlayerStatus.ReadyToPlay -> AudioEngineStatus.ReadyToPlay
                PlayerStatus.Failed -> AudioEngineStatus.Failed
            },
            currentPositionMs = ps.currentTime.seconds.inWholeMilliseconds,
            bufferedPositionMs = ps.bufferedDuration.seconds.inWholeMilliseconds,
            rate = ps.rate,
            error = ps.error?.toAudioEngineError(),
            currentKitharaItemId = selectedKitharaItemId,
        )
    }
        .stateIn(scope, SharingStarted.Eagerly, EnginePlayerSnapshot())

    val events: Flow<KitharaPlayerEvent> = merge(
        _events.asSharedFlow(),
        player.events.mapNotNull { event ->
            when (event) {
                is com.kithara.KitharaPlayerEvent.CurrentItemChanged -> {
                    currentItemId.value = event.itemId
                    KitharaPlayerEvent.CurrentItemChanged(event.itemId)
                }

                is com.kithara.KitharaPlayerEvent.QueueItemRemoved -> {
                    forgetInsertedItem(event.itemId)
                    null
                }

                is com.kithara.KitharaPlayerEvent.PlayedToEnd ->
                    KitharaPlayerEvent.PlayedToEnd(event.itemId)
            }
        },
    )

    fun play() = player.play()
    fun pause() = player.pause()
    suspend fun seek(seconds: Double): Boolean = player.seek(seconds)

    fun removeAllItems() {
        itemObservationJobs.values.forEach(Job::cancel)
        itemObservationJobs.clear()
        insertedItems.clear()
        currentItemId.value = null
        player.removeAllItems()
    }

    fun insertItem(url: String): KitharaItemHandle {
        val item = player.createItem(url)
        player.load(item)
        player.insert(item)
        insertedItems[item.id] = item
        observeItem(item)
        return RealKitharaItemHandle(item)
    }

    suspend fun selectItem(kitharaId: String) {
        val item = insertedItems[kitharaId]
        requireNotNull(item) { "Missing Kithara item in current window: $kitharaId" }

        awaitItemReady(item)

        val index = insertedItems.keys.indexOf(kitharaId)
        require(index >= 0) { "Missing Kithara item in current window: $kitharaId" }

        selectInsertedQueueItem(index = index)
    }

    fun removeItem(kitharaId: String) {
        val item = forgetInsertedItem(kitharaId) ?: return
        player.remove(item)
    }

    private fun forgetInsertedItem(kitharaId: String): KitharaPlayerItem? {
        itemObservationJobs.remove(kitharaId)?.cancel()
        val item = insertedItems.remove(kitharaId) ?: return null
        if (currentItemId.value == kitharaId) {
            currentItemId.value = null
        }
        return item
    }

    private fun observeItem(item: KitharaPlayerItem) {
        itemObservationJobs[item.id]?.cancel()
        itemObservationJobs[item.id] = CoroutineScope(scope.coroutineContext).launch {
            var lastDurationMs: Long? = null
            var lastStatus: EngineItemStatus = EngineItemStatus.Unknown

            item.state.collect { state ->
                val status = when (state.status) {
                    ItemStatus.Unknown -> EngineItemStatus.Unknown
                    ItemStatus.ReadyToPlay -> EngineItemStatus.ReadyToPlay
                    ItemStatus.Failed -> EngineItemStatus.Failed
                }
                val durationMs = state.duration?.seconds?.inWholeMilliseconds

                if (durationMs != null && durationMs != lastDurationMs) {
                    lastDurationMs = durationMs
                    _events.tryEmit(KitharaPlayerEvent.DurationDiscovered(item.id, durationMs))
                }

                if (status == lastStatus) return@collect
                lastStatus = status
                when (status) {
                    EngineItemStatus.ReadyToPlay ->
                        _events.emit(KitharaPlayerEvent.ItemReady(item.id, durationMs))

                    EngineItemStatus.Failed -> {
                        val error = state.error?.toAudioEngineError()
                            ?: AudioEngineError.LoadFailed("Unknown item error")
                        _events.emit(KitharaPlayerEvent.ItemFailed(item.id, error))
                    }

                    EngineItemStatus.Unknown -> Unit
                }
            }
        }
    }

    private suspend fun awaitItemReady(item: KitharaPlayerItem) {
        val state = item.state.first { observedState ->
            when (observedState.status) {
                ItemStatus.ReadyToPlay,
                ItemStatus.Failed -> true

                ItemStatus.Unknown -> false
            }
        }

        if (state.status == ItemStatus.Failed) {
            throw IllegalStateException(
                state.error?.toAudioEngineError()?.toFailureMessage() ?: "Item failed to load",
            )
        }
    }

    private suspend fun selectInsertedQueueItem(index: Int) {
        retryWithPolicy(
            attempts = SelectItemMaxAttempts,
            delayMs = SelectItemRetryDelayMs,
            shouldRetry = { error -> error is KitharaError.NotReady },
        ) {
            player.selectItem(index = index, autoplay = false)
        }
    }
}

private class RealKitharaItemHandle(
    item: KitharaPlayerItem,
) : KitharaItemHandle {
    override val kitharaId: String = item.id
}

private class RealKitharaPlayerDriver(
    private val delegate: KitharaPlayer = KitharaPlayer(
        config = KitharaPlayer.Config(
            gaplessMode = GaplessMode.SilenceTrim(
                params = SilenceTrimParams(
                    trimTrailing = true,
                )
            ),
        )
    ).apply {
        crossfadeDuration = 0f
    },
) : KitharaPlayerDriver {
    override val state: StateFlow<com.kithara.PlayerState> = delegate.state
    override val events: Flow<com.kithara.KitharaPlayerEvent> = delegate.events

    override fun play() = delegate.play()

    override fun pause() = delegate.pause()

    override suspend fun seek(seconds: Double): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            delegate.seek(seconds) { didFinish ->
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(didFinish))
                }
            }
        }

    override fun createItem(url: String): KitharaPlayerItem = KitharaPlayerItem(url = url)

    override fun load(item: KitharaPlayerItem) {
        item.load()
    }

    override fun insert(item: KitharaPlayerItem) {
        delegate.insert(item)
    }

    override fun selectItem(index: Int, autoplay: Boolean) {
        delegate.selectItem(index = index, autoplay = autoplay)
    }

    override fun remove(item: KitharaPlayerItem) {
        delegate.remove(item)
    }

    override fun removeAllItems() {
        delegate.removeAllItems()
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

private fun AudioEngineError.toFailureMessage(): String = when (this) {
    is AudioEngineError.LoadFailed -> message
    is AudioEngineError.StreamFailed -> message
    is AudioEngineError.EngineCrashed -> message
    AudioEngineError.SeekFailed -> "Seek failed"
}

@Suppress("TooGenericExceptionCaught")
internal suspend fun retryWithPolicy(
    attempts: Int,
    delayMs: Long,
    shouldRetry: (Throwable) -> Boolean,
    block: () -> Unit,
) {
    require(attempts > 0) { "attempts must be positive" }
    require(delayMs >= 0L) { "delayMs must be non-negative" }

    repeat(attempts - 1) {
        try {
            block()
            return
        } catch (error: Throwable) {
            if (!shouldRetry(error)) throw error
            delay(delayMs)
        }
    }

    block()
}

private const val SelectItemMaxAttempts = 10
private const val SelectItemRetryDelayMs = 25L
