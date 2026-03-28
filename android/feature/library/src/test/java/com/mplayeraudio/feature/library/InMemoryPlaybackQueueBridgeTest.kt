package com.mplayeraudio.feature.library

import com.mplayeraudio.core.player.PlaybackQueueItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryPlaybackQueueBridgeTest {

    @Test
    fun `playback commands mutate in-memory queue state`() = runTest {
        val bridge = InMemoryPlaybackQueueBridge()
        val queue = listOf(
            PlaybackQueueItem(
                id = "0:first",
                title = "First",
                subtitle = "Artist A",
                durationMs = 120_000L,
            ),
            PlaybackQueueItem(
                id = "1:second",
                title = "Second",
                subtitle = "Artist B",
                durationMs = 180_000L,
            ),
        )

        bridge.replaceQueue(queue = queue, startIndex = 0, autoPlay = true)
        bridge.seekTo(30_000L)
        bridge.skipNext()
        bridge.pause()
        bridge.skipPrevious()

        val playbackState = bridge.playbackState.first()

        assertEquals(0, playbackState.currentIndex)
        assertTrue(playbackState.controlsEnabled)
        assertTrue(playbackState.queue.isNotEmpty())
        assertTrue(playbackState.currentPositionMs == 0L)
        assertTrue(playbackState.currentItem?.title == "First")
        assertTrue(playbackState.currentItem?.subtitle == "Artist A")
        assertTrue(playbackState.queue.size == 2)
        assertTrue(playbackState.currentPositionMs <= playbackState.currentItem!!.durationMs)
        assertTrue(playbackState.currentIndex in playbackState.queue.indices)
        assertTrue(playbackState.currentItem != null)
        assertTrue(playbackState.currentItem!!.durationMs > 0L)
        assertTrue(playbackState.isPlaying)
    }

    @Test
    fun `replaceQueue preserves current item when same queue item remains`() = runTest {
        val bridge = InMemoryPlaybackQueueBridge()

        bridge.replaceQueue(
            queue = listOf(
                PlaybackQueueItem(id = "0:first", title = "First", subtitle = "Artist A", durationMs = 100_000L),
                PlaybackQueueItem(id = "1:second", title = "Second", subtitle = "Artist B", durationMs = 100_000L),
            ),
            startIndex = 1,
            autoPlay = true,
        )
        bridge.seekTo(45_000L)

        bridge.replaceQueue(
            queue = listOf(
                PlaybackQueueItem(id = "1:second", title = "Second", subtitle = "Artist B", durationMs = 100_000L),
                PlaybackQueueItem(id = "2:third", title = "Third", subtitle = "Artist C", durationMs = 100_000L),
            ),
        )

        val playbackState = bridge.playbackState.first()

        assertEquals(0, playbackState.currentIndex)
        assertEquals("1:second", playbackState.currentItem?.id)
        assertEquals(45_000L, playbackState.currentPositionMs)
        assertTrue(playbackState.isPlaying)
    }
}
