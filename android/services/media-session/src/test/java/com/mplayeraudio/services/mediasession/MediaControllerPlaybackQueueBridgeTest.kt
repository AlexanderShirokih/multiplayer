package com.mplayeraudio.services.mediasession

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControllerPlaybackQueueBridgeTest {

    @Test
    fun `planQueueReplacement preserves current item and playback state`() {
        val queue = listOf(
            queueItem(id = "0:first", durationMs = 120_000L),
            queueItem(id = "1:second", durationMs = 180_000L),
        )

        val replacement = planQueueReplacement(
            previousCurrentItemId = "1:second",
            previousPositionMs = 42_000L,
            wasPlaying = true,
            nextQueue = queue,
            requestedStartIndex = null,
            autoPlay = false,
        )

        assertEquals(1, replacement.startIndex)
        assertEquals(42_000L, replacement.startPositionMs)
        assertTrue(replacement.shouldPlay)
        assertEquals("1:second", replacement.activeItemId)
    }

    @Test
    fun `planQueueReplacement falls back to first item for new queue`() {
        val queue = listOf(
            queueItem(id = "0:first", durationMs = 120_000L),
            queueItem(id = "1:second", durationMs = 180_000L),
        )

        val replacement = planQueueReplacement(
            previousCurrentItemId = "old:item",
            previousPositionMs = 42_000L,
            wasPlaying = true,
            nextQueue = queue,
            requestedStartIndex = null,
            autoPlay = false,
        )

        assertEquals(null, replacement.startIndex)
        assertEquals(0L, replacement.startPositionMs)
        assertFalse(replacement.shouldPlay)
        assertNull(replacement.activeItemId)
    }

    @Test
    fun `resolveActiveQueueItemId ignores default first item after queue prepare`() {
        val queue = listOf(
            queueItem(id = "0:first", durationMs = 120_000L),
            queueItem(id = "1:second", durationMs = 180_000L),
        )

        val activeItemId = resolveActiveQueueItemId(
            existingActiveItemId = null,
            playerCurrentMediaItemId = "0:first",
            playerPlayWhenReady = false,
            playerIsPlaying = false,
            playerCurrentPositionMs = 0L,
            playerPlaybackState = androidx.media3.common.Player.STATE_READY,
            queue = queue,
        )

        assertNull(activeItemId)
    }

    @Test
    fun `resolveActiveQueueItemId adopts player item after explicit playback starts`() {
        val queue = listOf(
            queueItem(id = "0:first", durationMs = 120_000L),
            queueItem(id = "1:second", durationMs = 180_000L),
        )

        val activeItemId = resolveActiveQueueItemId(
            existingActiveItemId = null,
            playerCurrentMediaItemId = "1:second",
            playerPlayWhenReady = true,
            playerIsPlaying = false,
            playerCurrentPositionMs = 0L,
            playerPlaybackState = androidx.media3.common.Player.STATE_BUFFERING,
            queue = queue,
        )

        assertEquals("1:second", activeItemId)
    }

    @Test
    fun `playback error keeps active item but clears playing item`() {
        val queue = listOf(
            queueItem(id = "0:first", durationMs = 120_000L),
            queueItem(id = "1:second", durationMs = 180_000L),
        )

        val state = PlaybackQueueState(
            queue = queue,
            currentIndex = 1,
            isPlaying = false,
            currentPositionMs = 0L,
            controlsEnabled = true,
            playbackErrorMessage = "Network is unreachable",
        )

        assertEquals("1:second", state.activeItemId)
        assertNull(state.playingItemId)
    }
}

private fun queueItem(
    id: String,
    durationMs: Long,
): PlaybackQueueItem {
    return PlaybackQueueItem(
        id = id,
        trackId = YandexTrackId(id),
        source = PlayableSource.Remote(MusicProviderId.YandexMusic),
        title = id,
        subtitle = "Artist",
        durationMs = durationMs,
    )
}
