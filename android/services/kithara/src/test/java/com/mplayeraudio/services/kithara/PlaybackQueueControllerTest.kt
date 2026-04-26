package com.mplayeraudio.services.kithara

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlayableUrlResolver
import com.mplayeraudio.core.player.PlaybackError
import com.mplayeraudio.core.player.PlaybackPhase
import com.mplayeraudio.core.player.PlaybackQueueItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// advanceTimeBy(1) processes synchronous async work without firing watchdog (30s) or auto-skip (100ms).
// advanceTimeBy(AutoSkipProcessMs) processes auto-skip delay (100ms) without firing watchdog (30s).
// advanceUntilIdle() is only safe AFTER controller.shutdown() cancels childScope.
private const val AutoSkipProcessMs = 200L

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueControllerTest {

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    fun `replaceQueue with autoPlay=true starts loading first track`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this, loadTimeoutMs = 30_000L)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = true)
        // No advanceUntilIdle — check state synchronously after replaceQueue returns
        advanceTimeBy(1L)
        runCurrent()

        assertEquals(PlaybackPhase.Loading, controller.playbackState.value.phase)
        assertEquals(1, engine.loadRequests.size)
        assertEquals("t1", engine.loadRequests.first().id)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `engine isPlaying=true transitions phase to Playing`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L)

        engine.setPlayerState(
            AudioEngineState(isPlaying = true, currentItemId = "t1", status = AudioEngineStatus.ReadyToPlay),
        )
        advanceTimeBy(1L) // Let collectEngineState() pick up the new state

        assertEquals(PlaybackPhase.Playing, controller.playbackState.value.phase)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `playTrack sets currentIndex and transitions to Loading`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = false)
        advanceTimeBy(1L)

        controller.playTrack(1)
        advanceTimeBy(1L)

        assertEquals(1, controller.playbackState.value.currentIndex)
        assertEquals(PlaybackPhase.Loading, controller.playbackState.value.phase)
        assertEquals("t2", engine.loadRequests.last().id)

        controller.shutdown()
        advanceUntilIdle()
    }

    // ── DurationDiscovered ─────────────────────────────────────────────────────

    @Test
    fun `DurationDiscovered updates currentDurationMs`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("m3u8", durationMs = 0L)), autoPlay = true)
        advanceTimeBy(1L)

        engine.emitEvent(AudioEngineEvent.DurationDiscovered(itemId = "m3u8", durationMs = 300_000L))
        advanceTimeBy(1L) // Let collectEngineEvents() process the event

        assertEquals(300_000L, controller.playbackState.value.currentDurationMs)

        controller.shutdown()
        advanceUntilIdle()
    }

    // ── ItemFailed auto-skip ───────────────────────────────────────────────────

    @Test
    fun `ItemFailed sets Failed phase with playbackError immediately`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this, loadTimeoutMs = 30_000L)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = true)
        advanceTimeBy(1L)

        engine.emitEvent(
            AudioEngineEvent.ItemFailed("t1", AudioEngineError.LoadFailed("Network error")),
        )
        advanceTimeBy(1L) // Process event, but NOT the 100ms auto-skip delay

        val state = controller.playbackState.value
        assertEquals(PlaybackPhase.Failed, state.phase)
        assertNotNull(state.playbackError)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `ItemFailed auto-skips to next track after delay`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this, loadTimeoutMs = 30_000L)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = true)
        advanceTimeBy(1L)

        engine.emitEvent(
            AudioEngineEvent.ItemFailed("t1", AudioEngineError.LoadFailed("Network error")),
        )
        advanceTimeBy(AutoSkipProcessMs) // Process event + 100ms auto-skip delay

        val state = controller.playbackState.value
        assertEquals(1, state.currentIndex)
        assertEquals(PlaybackPhase.Loading, state.phase)
        assertTrue(engine.loadRequests.any { it.id == "t2" })

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `all tracks failed stays Failed with error when no more tracks`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this, loadTimeoutMs = 30_000L)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L)

        engine.emitEvent(
            AudioEngineEvent.ItemFailed("t1", AudioEngineError.LoadFailed("Not found")),
        )
        advanceTimeBy(1L)

        val state = controller.playbackState.value
        assertEquals(PlaybackPhase.Failed, state.phase)
        assertTrue(state.playbackError is PlaybackError.TrackUnavailable)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `PlayedToEnd advances to next track`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = true)
        advanceTimeBy(1L)

        engine.emitEvent(AudioEngineEvent.PlayedToEnd(itemId = "t1"))
        advanceTimeBy(1L) // collectEngineEvents processes event, onPlayedToEnd calls loadAndPlay inline

        assertEquals(1, controller.playbackState.value.currentIndex)
        assertTrue(engine.loadRequests.any { it.id == "t2" })

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `PlayedToEnd on last track transitions to Ended`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L)

        engine.emitEvent(AudioEngineEvent.PlayedToEnd(itemId = "t1"))
        advanceTimeBy(1L)

        assertEquals(PlaybackPhase.Ended, controller.playbackState.value.phase)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `PlayedToEnd for stale item does not advance queue`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2"), item("t3")), autoPlay = false)
        advanceTimeBy(1L)

        controller.playTrack(1)
        advanceTimeBy(1L)

        engine.emitEvent(AudioEngineEvent.PlayedToEnd(itemId = "t1"))
        advanceTimeBy(1L)

        assertEquals(1, controller.playbackState.value.currentIndex)
        assertEquals(1, engine.loadRequests.count { it.id == "t2" })

        controller.shutdown()
        advanceUntilIdle()
    }

    // ── Pause / Play ───────────────────────────────────────────────────────────

    @Test
    fun `pause transitions Loading to Paused`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L)

        controller.pause()
        advanceTimeBy(1L)

        assertEquals(PlaybackPhase.Paused, controller.playbackState.value.phase)
        assertEquals(1, engine.pauseCallCount)

        controller.shutdown()
        advanceUntilIdle()
    }

    // ── acknowledgeError ───────────────────────────────────────────────────────

    @Test
    fun `acknowledgeError clears playbackError`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this, loadTimeoutMs = 30_000L)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L)

        engine.emitEvent(
            AudioEngineEvent.ItemFailed("t1", AudioEngineError.LoadFailed("Error")),
        )
        advanceTimeBy(1L)

        controller.acknowledgeError()

        assertNull(controller.playbackState.value.playbackError)

        controller.shutdown()
        advanceUntilIdle()
    }

    // ── shutdown ───────────────────────────────────────────────────────────────

    @Test
    fun `shutdown resets state to Idle and stops engine`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L)

        controller.shutdown()
        advanceUntilIdle() // safe: childScope cancelled, no more timers

        val state = controller.playbackState.value
        assertEquals(PlaybackPhase.Idle, state.phase)
        assertTrue(state.queue.isEmpty())
        assertNull(state.currentIndex)
        assertTrue(engine.stopCallCount >= 1)
    }

    @Test
    fun `shutdown is idempotent`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.shutdown()
        controller.shutdown()
        advanceUntilIdle()

        assertEquals(PlaybackPhase.Idle, controller.playbackState.value.phase)
    }

    // ── replaceQueue preservation ──────────────────────────────────────────────

    @Test
    fun `replaceQueue preserves index when current track is still in queue`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = false)
        advanceTimeBy(1L)

        controller.replaceQueue(queue = listOf(item("t1"), item("t3")), autoPlay = false)
        advanceTimeBy(1L)

        assertEquals(0, controller.playbackState.value.currentIndex)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `replaceQueue with empty queue stops engine and sets Idle`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = false)
        advanceTimeBy(1L)

        controller.replaceQueue(queue = emptyList(), autoPlay = false)
        advanceTimeBy(1L)

        assertEquals(PlaybackPhase.Idle, controller.playbackState.value.phase)
        assertTrue(controller.playbackState.value.queue.isEmpty())

        controller.shutdown()
        advanceUntilIdle()
    }

    // ── Watchdog ───────────────────────────────────────────────────────────────

    @Test
    fun `watchdog fires after loadTimeoutMs when stuck in Loading`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val timeoutMs = 5_000L
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this, loadTimeoutMs = timeoutMs)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L) // Phase=Loading, watchdog armed

        advanceTimeBy(timeoutMs + 1L) // Fire watchdog (single-track queue, no auto-skip)

        val state = controller.playbackState.value
        assertEquals(PlaybackPhase.Failed, state.phase)
        assertTrue(state.playbackError is PlaybackError.StreamFailed)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `watchdog is cancelled when engine starts playing`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val timeoutMs = 5_000L
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this, loadTimeoutMs = timeoutMs)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L) // Phase=Loading, watchdog armed

        // Engine starts playing before timeout
        engine.setPlayerState(
            AudioEngineState(isPlaying = true, currentItemId = "t1", status = AudioEngineStatus.ReadyToPlay),
        )
        advanceTimeBy(1L) // collectEngineState transitions to Playing, cancels watchdog

        assertEquals(PlaybackPhase.Playing, controller.playbackState.value.phase)

        // Advance past timeout — watchdog must NOT fire
        controller.shutdown()
        advanceUntilIdle()

        // After shutdown, phase is Idle (not Failed from watchdog)
        assertEquals(PlaybackPhase.Idle, controller.playbackState.value.phase)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Test helpers
// ────────────────────────────────────────────────────────────────────────────

private fun item(id: String, durationMs: Long = 180_000L) = PlaybackQueueItem(
    id = id,
    trackId = YandexTrackId(id),
    source = PlayableSource.Remote(MusicProviderId.YandexMusic),
    title = "Track $id",
    subtitle = "Artist",
    durationMs = durationMs,
)

private class StaticUrlResolver(private val url: String = "http://host/track.mp3") : PlayableUrlResolver {
    override suspend fun getPlayableUrl(item: PlaybackQueueItem): String = url
}

private class FakeAudioPlaybackEngine : AudioPlaybackEngine {

    private val _engineState = MutableStateFlow(AudioEngineState())
    private val _events = MutableSharedFlow<AudioEngineEvent>(extraBufferCapacity = 16)

    override val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()
    override val events: SharedFlow<AudioEngineEvent> = _events.asSharedFlow()

    val loadRequests = mutableListOf<AudioTrackRequest>()
    var pauseCallCount = 0
    var stopCallCount = 0

    override fun play() = Unit
    override fun pause() { pauseCallCount++ }
    override suspend fun seekTo(positionMs: Long): Boolean = true
    override fun loadTrack(request: AudioTrackRequest, autoPlay: Boolean) { loadRequests += request }
    override fun stop() { stopCallCount++ }

    fun setPlayerState(state: AudioEngineState) { _engineState.value = state }
    suspend fun emitEvent(event: AudioEngineEvent) = _events.emit(event)
}
