package com.mplayeraudio.services.mediasession

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlaybackQueueItem
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
        assertEquals("1:second", replacement.selectedItemId)
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
        assertNull(replacement.selectedItemId)
    }

    @Test
    fun `resolveSelectedQueueItemId ignores default first item after queue prepare`() {
        val queue = listOf(
            queueItem(id = "0:first", durationMs = 120_000L),
            queueItem(id = "1:second", durationMs = 180_000L),
        )

        val selectedItemId = resolveSelectedQueueItemId(
            existingSelectedItemId = null,
            playerCurrentMediaItemId = "0:first",
            playerPlayWhenReady = false,
            playerIsPlaying = false,
            playerCurrentPositionMs = 0L,
            playerPlaybackState = androidx.media3.common.Player.STATE_READY,
            queue = queue,
        )

        assertNull(selectedItemId)
    }

    @Test
    fun `resolveSelectedQueueItemId adopts player item after explicit playback starts`() {
        val queue = listOf(
            queueItem(id = "0:first", durationMs = 120_000L),
            queueItem(id = "1:second", durationMs = 180_000L),
        )

        val selectedItemId = resolveSelectedQueueItemId(
            existingSelectedItemId = null,
            playerCurrentMediaItemId = "1:second",
            playerPlayWhenReady = true,
            playerIsPlaying = false,
            playerCurrentPositionMs = 0L,
            playerPlaybackState = androidx.media3.common.Player.STATE_BUFFERING,
            queue = queue,
        )

        assertEquals("1:second", selectedItemId)
    }
}

private fun queueItem(
    id: String,
    durationMs: Long,
): PlaybackQueueItem {
    return PlaybackQueueItem(
        id = id,
        trackId = TrackId(id),
        source = PlayableSource.Remote(MusicProviderId.YandexMusic),
        title = id,
        subtitle = "Artist",
        durationMs = durationMs,
    )
}
