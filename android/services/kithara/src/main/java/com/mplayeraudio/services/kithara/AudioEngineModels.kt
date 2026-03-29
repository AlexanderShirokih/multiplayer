package com.mplayeraudio.services.kithara

import com.kithara.KitharaError

data class AudioTrackRequest(
    val id: String,
    val url: String,
)

data class AudioEngineState(
    val status: AudioEngineStatus = AudioEngineStatus.Idle,
    val currentPositionMs: Long = 0L,
    val durationMs: Long? = null,
    val currentItemId: String? = null,
    val isPlaying: Boolean = false,
    val error: KitharaError? = null,
)

enum class AudioEngineStatus {
    Idle,
    ReadyToPlay,
    Failed,
}

sealed interface AudioEngineEvent {
    data class CurrentItemChanged(val itemId: String?) : AudioEngineEvent
    data object PlayedToEnd : AudioEngineEvent
    data class ItemFailed(val itemId: String?, val reason: String) : AudioEngineEvent
}
