package com.mplayeraudio.core.player

import kotlinx.coroutines.flow.Flow

data class NowPlayingStripExternalState(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val controlsEnabled: Boolean = true,
)

data class NowPlayingStripState(
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progressFraction: Float = 0f,
    val isSeekInProgress: Boolean = false,
    val displayedProgressFraction: Float = 0f,
    val controlsEnabled: Boolean = true,
)

sealed interface NowPlayingStripAction {
    data object PreviousClicked : NowPlayingStripAction

    data object PlayPauseClicked : NowPlayingStripAction

    data object NextClicked : NowPlayingStripAction

    data object SeekStarted : NowPlayingStripAction

    data class SeekChanged(
        val fraction: Float,
    ) : NowPlayingStripAction

    data class SeekFinished(
        val fraction: Float,
    ) : NowPlayingStripAction
}

interface NowPlayingStripController {
    val state: Flow<NowPlayingStripExternalState>

    suspend fun play()

    suspend fun pause()

    suspend fun skipNext()

    suspend fun skipPrevious()

    suspend fun seekTo(positionMs: Long)
}
