package com.mplayeraudio.services.kithara

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface AudioPlaybackEngine {
    val engineState: StateFlow<AudioEngineState>
    val events: SharedFlow<AudioEngineEvent>

    fun play()
    fun pause()
    suspend fun seekTo(positionMs: Long): Boolean
    fun setQueueWindow(
        current: AudioTrackRequest,
        next: AudioTrackRequest?,
        autoPlay: Boolean,
    )

    fun appendNext(next: AudioTrackRequest)

    suspend fun selectInWindow(appItemId: String, autoPlay: Boolean): Boolean

    fun pruneWindow(keepAppItemIds: Set<String>)

    fun stop()
}
