package com.mplayeraudio.core.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NowPlayingStripViewModel(
    private val controller: NowPlayingStripController,
) : ViewModel() {
    private var externalState = NowPlayingStripExternalState()
    private var isSeeking = false
    private var seekPreviewFraction: Float? = null

    private val _state = MutableStateFlow(externalState.toUiState())
    val state: StateFlow<NowPlayingStripState> = _state.asStateFlow()

    init {
        controller.state
            .onEach { state ->
                externalState = state
                updateUiState()
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: NowPlayingStripAction) {
        when (action) {
            NowPlayingStripAction.NextClicked -> submitCommand {
                controller.skipNext()
            }

            NowPlayingStripAction.PlayPauseClicked -> submitCommand {
                if (externalState.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
            }

            NowPlayingStripAction.PreviousClicked -> submitCommand {
                controller.skipPrevious()
            }

            is NowPlayingStripAction.SeekChanged -> handleSeekChanged(action.fraction)

            NowPlayingStripAction.SeekStarted -> handleSeekStarted()

            is NowPlayingStripAction.SeekFinished -> handleSeekFinished(action.fraction)
        }
    }

    internal fun dispose() {
        viewModelScope.cancel()
    }

    private fun submitCommand(command: suspend () -> Unit) {
        if (!externalState.controlsEnabled) return
        viewModelScope.launch {
            command()
        }
    }

    private fun handleSeekStarted() {
        if (!canSeek()) return

        isSeeking = true
        seekPreviewFraction = externalState.progressFraction()
        updateUiState()
    }

    private fun handleSeekChanged(fraction: Float) {
        if (!canSeek()) return

        isSeeking = true
        seekPreviewFraction = fraction.coerceIn(0f, 1f)
        updateUiState()
    }

    private fun handleSeekFinished(fraction: Float) {
        if (!canSeek() || !isSeeking) return

        val finalFraction = fraction.coerceIn(0f, 1f)
        val targetPositionMs = (externalState.durationMs * finalFraction).toLong()

        viewModelScope.launch {
            controller.seekTo(targetPositionMs)
        }

        isSeeking = false
        seekPreviewFraction = null
        updateUiState()
    }

    private fun canSeek(): Boolean {
        return externalState.controlsEnabled && externalState.durationMs > 0L
    }

    private fun updateUiState() {
        _state.update {
            externalState.toUiState(
                isSeekInProgress = isSeeking,
                seekPreviewFraction = seekPreviewFraction,
            )
        }
    }

    private fun NowPlayingStripExternalState.toUiState(
        isSeekInProgress: Boolean = false,
        seekPreviewFraction: Float? = null,
    ): NowPlayingStripState {
        val progressFraction = progressFraction()
        return NowPlayingStripState(
            title = title,
            subtitle = subtitle,
            isPlaying = isPlaying,
            currentPositionMs = currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)),
            durationMs = durationMs.coerceAtLeast(0L),
            progressFraction = progressFraction,
            isSeekInProgress = isSeekInProgress,
            displayedProgressFraction = seekPreviewFraction ?: progressFraction,
            controlsEnabled = controlsEnabled,
        )
    }

    private fun NowPlayingStripExternalState.progressFraction(): Float {
        if (durationMs <= 0L) return 0f
        return (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
}
