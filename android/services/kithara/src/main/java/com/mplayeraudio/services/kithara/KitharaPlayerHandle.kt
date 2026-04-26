package com.mplayeraudio.services.kithara

import com.kithara.ItemStatus
import com.kithara.KitharaPlayer
import com.kithara.KitharaPlayerEvent
import com.kithara.KitharaPlayerItem
import com.kithara.PlayerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

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
    data object PlayedToEnd : EnginePlayerEvent
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

    /** Создаёт, загружает и вставляет новый item в плеер, возвращает дескриптор. */
    fun createAndLoadItem(url: String): KitharaItemHandle

    fun removeAllItems()
}

// ────────────────────────────────────────────────────────────────────────────
// Real (FFI) implementations — Kithara-типы используются только здесь
// ────────────────────────────────────────────────────────────────────────────

internal class RealKitharaPlayerHandle(private val scope: CoroutineScope) : KitharaPlayerHandle {

    private val player = KitharaPlayer().apply {
        crossfadeDuration = 0f
    }

    override val snapshots: StateFlow<EnginePlayerSnapshot> = player.state
        .map { ps ->
            EnginePlayerSnapshot(
                status = when (ps.status) {
                    PlayerStatus.Unknown -> AudioEngineStatus.Idle
                    PlayerStatus.ReadyToPlay -> AudioEngineStatus.ReadyToPlay
                    PlayerStatus.Failed -> AudioEngineStatus.Failed
                },
                currentPositionMs = (ps.currentTime * MsPerSecond).toLong(),
                bufferedPositionMs = (ps.bufferedDuration * MsPerSecond).toLong(),
                rate = ps.rate,
                error = ps.error?.toAudioEngineError(),
                currentKitharaItemId = ps.items.firstOrNull()?.id,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, EnginePlayerSnapshot())

    override val events: Flow<EnginePlayerEvent> = player.events
        .mapNotNull { event ->
            when (event) {
                is KitharaPlayerEvent.CurrentItemChanged ->
                    EnginePlayerEvent.CurrentItemChanged(event.itemId)
                is KitharaPlayerEvent.PlayedToEnd ->
                    EnginePlayerEvent.PlayedToEnd
            }
        }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seek(seconds: Double, callback: (Boolean) -> Unit) =
        player.seek(seconds, callback)
    override fun removeAllItems() = player.removeAllItems()

    override fun createAndLoadItem(url: String): KitharaItemHandle {
        val item = KitharaPlayerItem(url = url)
        item.load()
        player.insert(item)
        return RealKitharaItemHandle(item, scope)
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
                durationMs = state.duration?.let { (it * MsPerSecond).toLong() },
                error = state.error?.toAudioEngineError(),
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, EngineItemSnapshot())
}

private const val MsPerSecond = 1_000.0
