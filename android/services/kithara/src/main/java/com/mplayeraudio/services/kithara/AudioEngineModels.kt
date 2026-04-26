package com.mplayeraudio.services.kithara

data class AudioTrackRequest(
    val id: String,
    val url: String,
)

data class AudioEngineState(
    val status: AudioEngineStatus = AudioEngineStatus.Idle,
    val currentPositionMs: Long = 0L,
    val durationMs: Long? = null,
    val bufferedPositionMs: Long = 0L,
    val currentItemId: String? = null,
    val isPlaying: Boolean = false,
    val error: AudioEngineError? = null,
)

enum class AudioEngineStatus {
    Idle,
    ReadyToPlay,
    Failed,
}

sealed interface AudioEngineEvent {
    data class CurrentItemChanged(val itemId: String?) : AudioEngineEvent
    data object PlayedToEnd : AudioEngineEvent
    data class ItemFailed(val itemId: String, val reason: AudioEngineError) : AudioEngineEvent
    data class EngineFailed(val reason: AudioEngineError) : AudioEngineEvent
    data class DurationDiscovered(val itemId: String, val durationMs: Long) : AudioEngineEvent
}

sealed interface AudioEngineError {
    data class LoadFailed(val message: String) : AudioEngineError
    data class StreamFailed(val message: String) : AudioEngineError
    data class EngineCrashed(val message: String) : AudioEngineError
    data object SeekFailed : AudioEngineError
}
