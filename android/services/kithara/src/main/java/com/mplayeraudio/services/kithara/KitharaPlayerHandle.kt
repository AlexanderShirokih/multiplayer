package com.mplayeraudio.services.kithara

import com.kithara.ItemStatus
import com.kithara.KitharaError
import com.kithara.KitharaPlayer
import com.kithara.KitharaPlayerEvent
import com.kithara.KitharaPlayerItem
import com.kithara.PlayerStatus
import com.kithara.ffi.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.lang.reflect.Method
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

/** Событие плеера, отображённое из Kithara без утечки FFI-типов. */
internal sealed interface EnginePlayerEvent {
    data class CurrentItemChanged(val kitharaItemId: String?) : EnginePlayerEvent
    data class PlayedToEnd(val kitharaItemId: String) : EnginePlayerEvent
}

/**
 * Дескриптор конкретного item'а, загруженного в [KitharaPlayerHandle].
 * Интерфейс использует только наши типы — тесты не зависят от Kithara classpath.
 */
internal interface KitharaItemHandle {
    val kitharaId: String
    val snapshots: StateFlow<EngineItemSnapshot>
}

/**
 * Тонкая обёртка над [KitharaPlayer] для изоляции FFI-вызовов.
 * Интерфейс не содержит com.kithara.* типов — позволяет подменять
 * реальный плеер фейком в unit-тестах.
 */
internal interface KitharaPlayerHandle {
    val snapshots: StateFlow<EnginePlayerSnapshot>
    val events: Flow<EnginePlayerEvent>

    fun play()
    fun pause()
    fun seek(seconds: Double, callback: (Boolean) -> Unit)
    fun insertItem(url: String): KitharaItemHandle
    fun selectItem(kitharaId: String)
    fun removeItem(kitharaId: String)
    fun removeAllItems()
}

// ────────────────────────────────────────────────────────────────────────────
// Real (FFI) implementations — Kithara-типы используются только здесь
// ────────────────────────────────────────────────────────────────────────────

internal class RealKitharaPlayerHandle(private val scope: CoroutineScope) : KitharaPlayerHandle {

    private val player = KitharaPlayer().apply {
        crossfadeDuration = 0f
    }
    private val insertedItems = LinkedHashMap<String, KitharaPlayerItem>()
    private val currentItemId = MutableStateFlow<String?>(null)
    private val ffiPlayer: AudioPlayer by lazy(LazyThreadSafetyMode.NONE) {
        val field = KitharaPlayer::class.java.getDeclaredField("inner")
        field.isAccessible = true
        field.get(player) as AudioPlayer
    }
    private val selectItemMethod: Method by lazy(LazyThreadSafetyMode.NONE) {
        AudioPlayer::class.java.methods.first { method ->
            method.name.startsWith("selectItem") &&
                method.parameterTypes.contentEquals(
                    arrayOf(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
                )
        }
    }

    override val snapshots: StateFlow<EnginePlayerSnapshot> = combine(
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

    override val events: Flow<EnginePlayerEvent> = player.events
        .mapNotNull { event ->
            when (event) {
                is KitharaPlayerEvent.CurrentItemChanged -> {
                    currentItemId.value = event.itemId
                    EnginePlayerEvent.CurrentItemChanged(event.itemId)
                }
                is KitharaPlayerEvent.PlayedToEnd ->
                    EnginePlayerEvent.PlayedToEnd(event.itemId)
            }
        }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seek(seconds: Double, callback: (Boolean) -> Unit) =
        player.seek(seconds, callback)
    override fun removeAllItems() {
        insertedItems.clear()
        currentItemId.value = null
        player.removeAllItems()
    }

    override fun insertItem(url: String): KitharaItemHandle {
        val item = KitharaPlayerItem(url = url)
        item.load()
        player.insert(item)
        insertedItems[item.id] = item
        return RealKitharaItemHandle(item, scope)
    }

    override fun selectItem(kitharaId: String) {
        val index = insertedItems.keys.indexOf(kitharaId)
        require(index >= 0) { "Missing Kithara item in current window: $kitharaId" }

        currentItemId.value = kitharaId
        selectItemMethod.invoke(ffiPlayer, index, false)
    }

    override fun removeItem(kitharaId: String) {
        val item = insertedItems.remove(kitharaId) ?: return
        if (currentItemId.value == kitharaId) {
            currentItemId.value = null
        }
        player.remove(item)
    }
}

private class RealKitharaItemHandle(
    item: KitharaPlayerItem,
    scope: CoroutineScope,
) : KitharaItemHandle {

    override val kitharaId: String = item.id

    override val snapshots: StateFlow<EngineItemSnapshot> = item.state
        .map { state ->
            EngineItemSnapshot(
                status = when (state.status) {
                    ItemStatus.Unknown -> EngineItemStatus.Unknown
                    ItemStatus.ReadyToPlay -> EngineItemStatus.ReadyToPlay
                    ItemStatus.Failed -> EngineItemStatus.Failed
                },
                durationMs = state.duration?.seconds?.inWholeMilliseconds,
                error = state.error?.toAudioEngineError(),
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, EngineItemSnapshot())
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
