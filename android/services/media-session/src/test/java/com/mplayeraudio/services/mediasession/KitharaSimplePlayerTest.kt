package com.mplayeraudio.services.mediasession

import androidx.media3.common.Player
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.UserPlaylistTrackId
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlayableUrlResolver
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.services.kithara.AudioEngineEvent
import com.mplayeraudio.services.kithara.AudioEngineState
import com.mplayeraudio.services.kithara.AudioPlaybackEngine
import com.mplayeraudio.services.kithara.AudioTrackRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class KitharaSimplePlayerTest {

    @Test
    fun `illegal argument while resolving url becomes player error`() = runTest {
        val player = KitharaSimplePlayer(
            context = RuntimeEnvironment.getApplication(),
            engine = FakeAudioPlaybackEngine(),
            urlResolver = ThrowingPlayableUrlResolver(IllegalArgumentException("Invalid track ID")),
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        player.setMediaItems(
            listOf(queueItem().toMediaItem()),
            0,
            0L,
        )
        player.playWhenReady = true
        advanceUntilIdle()

        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertNotNull(player.playerError)
        assertTrue(player.playerError?.cause is IllegalArgumentException)

        player.release()
    }

    @Test
    fun `yandex music item failure becomes player error without retry`() = runTest {
        assertItemFailureBecomesPlayerError(
            trackId = YandexTrackId("track"),
            source = PlayableSource.Remote(MusicProviderId.YandexMusic),
        )
    }

    @Test
    fun `user playlist item failure becomes player error without retry`() = runTest {
        assertItemFailureBecomesPlayerError(
            trackId = UserPlaylistTrackId(7L),
            source = PlayableSource.Remote(MusicProviderId.UserPlaylists),
        )
    }

    @Test
    fun `local url item failure becomes player error without retry`() = runTest {
        assertItemFailureBecomesPlayerError(
            trackId = UserPlaylistTrackId(7L),
            source = PlayableSource.Local("http://192.168.3.11:3444/stream/track.m3u8"),
        )
    }

    private suspend fun TestScope.assertItemFailureBecomesPlayerError(
        trackId: TrackId,
        source: PlayableSource,
    ) {
        val engine = FakeAudioPlaybackEngine()
        val player = KitharaSimplePlayer(
            context = RuntimeEnvironment.getApplication(),
            engine = engine,
            urlResolver = StaticPlayableUrlResolver("http://192.168.3.11:3444/stream/track.m3u8"),
            scope = this,
            looper = android.os.Looper.getMainLooper(),
        )

        player.setMediaItems(
            listOf(
                queueItem(
                    trackId = trackId,
                    source = source,
                ).toMediaItem(),
            ),
            0,
            0L,
        )
        player.playWhenReady = true
        advanceUntilIdle()

        assertEquals(1, engine.loadRequests.size)

        engine.events.emit(
            AudioEngineEvent.ItemFailed(
                itemId = "0:track",
                reason = "Network is unreachable",
            ),
        )
        advanceUntilIdle()

        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertFalse(player.isPlaying)
        assertNotNull(player.playerError)
        assertTrue(player.playerError?.cause is IllegalStateException)
        assertEquals(1, engine.loadRequests.size)

        player.release()
    }
}

private fun queueItem(
    trackId: TrackId = YandexTrackId("track"),
    source: PlayableSource = PlayableSource.Remote(MusicProviderId.YandexMusic),
): PlaybackQueueItem {
    return PlaybackQueueItem(
        id = "0:track",
        trackId = trackId,
        source = source,
        title = "Track",
        subtitle = "Artist",
        durationMs = 180_000L,
    )
}

private class ThrowingPlayableUrlResolver(
    private val error: IllegalArgumentException,
) : PlayableUrlResolver {
    override suspend fun getPlayableUrl(item: PlaybackQueueItem): String {
        throw error
    }
}

private class StaticPlayableUrlResolver(
    private val url: String,
) : PlayableUrlResolver {
    override suspend fun getPlayableUrl(item: PlaybackQueueItem): String = url
}

private class FakeAudioPlaybackEngine : AudioPlaybackEngine {
    override val engineState = MutableStateFlow(AudioEngineState())
    override val events = MutableSharedFlow<AudioEngineEvent>()
    val loadRequests = mutableListOf<AudioTrackRequest>()

    override fun play() = Unit

    override fun pause() = Unit

    override suspend fun seekTo(positionMs: Long): Boolean = true

    override fun loadTrack(request: AudioTrackRequest) {
        loadRequests += request
    }

    override fun stop() = Unit
}
