package com.mplayeraudio.services.kithara

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AudioPlaybackEngine {
    val engineState: StateFlow<AudioEngineState>
    val events: SharedFlow<AudioEngineEvent>

    fun play()
    fun pause()
    suspend fun seekTo(positionMs: Long): Boolean
    fun loadTrack(request: AudioTrackRequest, autoPlay: Boolean = true)
    fun stop()
}
