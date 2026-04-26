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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val bridge = FakePlaybackQueueBridge()
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        bridge.setState(
            PlaybackQueueState(
                queue = listOf(queueItem()),
                currentIndex = 0,
                phase = PlaybackPhase.Playing,
            ),
        )
        advanceUntilIdle()

        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(true, player.playWhenReady)

        player.release()
    }

    @Test
    fun `Paused phase maps to STATE_READY with playWhenReady=false`() = runTest {
        val bridge = FakePlaybackQueueBridge()
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        bridge.setState(
            PlaybackQueueState(
                queue = listOf(queueItem()),
                currentIndex = 0,
                phase = PlaybackPhase.Paused,
            ),
        )
        advanceUntilIdle()

        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(false, player.playWhenReady)

        player.release()
    }

    @Test
    fun `Failed phase maps to STATE_IDLE with playerError set`() = runTest {
        val bridge = FakePlaybackQueueBridge()
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        bridge.setState(
            PlaybackQueueState(
                queue = listOf(queueItem()),
                currentIndex = 0,
                phase = PlaybackPhase.Failed,
                playbackError = PlaybackError.TrackUnavailable("0:track", "Network error"),
            ),
        )
        advanceUntilIdle()

        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertNotNull(player.playerError)

        player.release()
    }

    @Test
    fun `Idle phase maps to STATE_IDLE without error`() = runTest {
        val bridge = FakePlaybackQueueBridge()
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        bridge.setState(PlaybackQueueState())
        advanceUntilIdle()

        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertNull(player.playerError)

        player.release()
    }

    @Test
    fun `currentDurationMs overrides static item duration in playlist`() = runTest {
        val bridge = FakePlaybackQueueBridge()
        val player = KitharaSimplePlayer(
            controller = bridge,
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        bridge.setState(
            PlaybackQueueState(
                queue = listOf(queueItem(durationMs = 0L)),
                currentIndex = 0,
                phase = PlaybackPhase.Playing,
                currentDurationMs = 180_000L,
            ),
        )
        advanceUntilIdle()

        assertEquals(180_000L, player.contentDuration)

        player.release()
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

private class FakePlaybackQueueBridge : PlaybackQueueBridge {

    private val stateFlow = MutableStateFlow(PlaybackQueueState())

    override val playbackState: StateFlow<PlaybackQueueState> = stateFlow.asStateFlow()
    override val state: Flow<NowPlayingStripExternalState> = emptyFlow()

    fun setState(state: PlaybackQueueState) { stateFlow.value = state }

    override suspend fun replaceQueue(queue: List<PlaybackQueueItem>, startIndex: Int?, autoPlay: Boolean) = Unit
    override suspend fun playTrack(index: Int) = Unit
    override suspend fun play() = Unit
    override suspend fun pause() = Unit
    override suspend fun skipNext() = Unit
    override suspend fun skipPrevious() = Unit
    override suspend fun seekTo(positionMs: Long) = Unit
    override fun acknowledgeError() = Unit
    override fun shutdown() = Unit
}
