package com.mplayeraudio.services.mediasession

import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.mplayeraudio.core.player.PlaybackQueueItem

internal data class PlayerSnapshot(
    val queue: List<PlaybackQueueItem> = emptyList(),
    val currentIndex: Int? = null,
    val playWhenReady: Boolean = false,
    val playWhenReadyChangeReason: Int = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
    val isPlaying: Boolean = false,
    val contentPositionMs: Long = 0L,
    val contentPositionUpdatedAtMs: Long = SystemClock.elapsedRealtime(),
    val playbackState: Int = Player.STATE_IDLE,
    val playerError: PlaybackException? = null,
    val audioAttributes: AudioAttributes = AudioAttributes.DEFAULT,
) {
    val currentItem: PlaybackQueueItem?
        get() = currentIndex?.let(queue::getOrNull)

    fun withPosition(positionMs: Long, nowElapsedMs: Long = SystemClock.elapsedRealtime()): PlayerSnapshot {
        return copy(
            contentPositionMs = positionMs.coerceAtLeast(0L),
            contentPositionUpdatedAtMs = nowElapsedMs,
        )
    }

    fun extrapolatedPositionMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        if (!isPlaying) {
            return contentPositionMs
        }
        val durationCap = currentItem?.durationMs?.takeIf { it > 0L }
        val advanced = contentPositionMs + (nowElapsedMs - contentPositionUpdatedAtMs).coerceAtLeast(0L)
        return if (durationCap != null) advanced.coerceAtMost(durationCap) else advanced
    }
}
