package com.mplayeraudio.services.mediasession

import androidx.media3.common.Player
import com.mplayeraudio.core.player.NowPlayingStripExternalState
import com.mplayeraudio.core.player.PlaybackError
import com.mplayeraudio.core.player.PlaybackPhase
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class KitharaSimplePlayerTest {

    @Test
    fun `Playing phase maps to STATE_READY with playWhenReady=true`() = runTest {
        val stateFlow = MutableStateFlow(PlaybackQueueState())
        val bridge = playbackQueueBridgeMock(stateFlow)
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        stateFlow.value =
            PlaybackQueueState(
                queue = listOf(queueItem()),
                currentIndex = 0,
                phase = PlaybackPhase.Playing,
            ),
        advanceUntilIdle()

        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(true, player.playWhenReady)

        player.release()
    }

    @Test
    fun `Paused phase maps to STATE_READY with playWhenReady=false`() = runTest {
        val stateFlow = MutableStateFlow(PlaybackQueueState())
        val bridge = playbackQueueBridgeMock(stateFlow)
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        stateFlow.value =
            PlaybackQueueState(
                queue = listOf(queueItem()),
                currentIndex = 0,
                phase = PlaybackPhase.Paused,
            ),
        advanceUntilIdle()

        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(false, player.playWhenReady)

        player.release()
    }

    @Test
    fun `Failed phase maps to STATE_IDLE with playerError set`() = runTest {
        val stateFlow = MutableStateFlow(PlaybackQueueState())
        val bridge = playbackQueueBridgeMock(stateFlow)
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        stateFlow.value =
            PlaybackQueueState(
                queue = listOf(queueItem()),
                currentIndex = 0,
                phase = PlaybackPhase.Failed,
                playbackError = PlaybackError.TrackUnavailable("0:track", "Network error"),
            ),
        advanceUntilIdle()

        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertNotNull(player.playerError)

        player.release()
    }

    @Test
    fun `Idle phase maps to STATE_IDLE without error`() = runTest {
        val stateFlow = MutableStateFlow(PlaybackQueueState())
        val bridge = playbackQueueBridgeMock(stateFlow)
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        stateFlow.value = PlaybackQueueState()
        advanceUntilIdle()

        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertNull(player.playerError)

        player.release()
    }

    @Test
    fun `currentDurationMs overrides static item duration in playlist`() = runTest {
        val stateFlow = MutableStateFlow(PlaybackQueueState())
        val bridge = playbackQueueBridgeMock(stateFlow)
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        stateFlow.value =
            PlaybackQueueState(
                queue = listOf(queueItem(durationMs = 0L)),
                currentIndex = 0,
                phase = PlaybackPhase.Playing,
                currentDurationMs = 180_000L,
            ),
        advanceUntilIdle()

        assertEquals(180_000L, player.contentDuration)

        player.release()
    }
}

private fun playbackQueueBridgeMock(
    stateFlow: MutableStateFlow<PlaybackQueueState>,
): PlaybackQueueBridge {
    return mockk(relaxed = true) {
        every { playbackState } returns stateFlow
        every { state } returns emptyFlow()
    }
}

private fun queueItem(durationMs: Long = 180_000L): PlaybackQueueItem {
    return PlaybackQueueItem(
        id = "0:track",
        trackId = YandexTrackId("track"),
        source = PlayableSource.Remote(com.mplayeraudio.core.domain.musiclibrary.MusicProviderId.YandexMusic),
        title = "Track",
        subtitle = "Artist",
        durationMs = durationMs,
    )
}
