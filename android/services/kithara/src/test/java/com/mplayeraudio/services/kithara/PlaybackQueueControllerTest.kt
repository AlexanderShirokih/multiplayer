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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueControllerTest {

    @Test
    fun `replaceQueue with autoPlay=true sets current-next window`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2"), item("t3")), autoPlay = true)
        advanceTimeBy(1L)

        assertEquals(PlaybackPhase.Loading, controller.playbackState.value.phase)
        assertEquals(
            listOf(
                WindowOp.SetWindow(
                    current = AudioTrackRequest("t1", "http://host/t1.mp3"),
                    next = AudioTrackRequest("t2", "http://host/t2.mp3"),
                    autoPlay = true,
                ),
            ),
            engine.windowOps,
        )

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `replaceQueue preserving current prunes window without reloading`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = true)
        advanceTimeBy(1L)
        engine.windowOps.clear()

        controller.replaceQueue(queue = listOf(item("t1"), item("t3")), autoPlay = false)
        advanceTimeBy(1L)

        assertEquals(
            listOf(
                WindowOp.PruneWindow(setOf("t1", "t3")),
                WindowOp.AppendNext(AudioTrackRequest("t3", "http://host/t3.mp3")),
            ),
            engine.windowOps,
        )
        assertEquals(0, engine.stopCallCount)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `CurrentItemChanged advances queue without reload and extends window`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2"), item("t3")), autoPlay = true)
        advanceTimeBy(1L)
        engine.windowOps.clear()

        engine.emitEvent(AudioEngineEvent.CurrentItemChanged("t2"))
        advanceTimeBy(1L)

        assertEquals(1, controller.playbackState.value.currentIndex)
        assertEquals(
            listOf(
                WindowOp.PruneWindow(setOf("t2", "t3")),
                WindowOp.AppendNext(AudioTrackRequest("t3", "http://host/t3.mp3")),
            ),
            engine.windowOps,
        )
        assertFalse(engine.windowOps.any { it is WindowOp.SetWindow })

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `PlayedToEnd in mid queue does not trigger reload`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = true)
        advanceTimeBy(1L)
        engine.windowOps.clear()

        engine.emitEvent(AudioEngineEvent.PlayedToEnd("t1"))
        advanceTimeBy(1L)

        assertTrue(engine.windowOps.isEmpty())
        assertEquals(0, controller.playbackState.value.currentIndex)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `PlayedToEnd on last track transitions to Ended`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L)

        engine.emitEvent(AudioEngineEvent.PlayedToEnd("t1"))
        advanceTimeBy(1L)

        assertEquals(PlaybackPhase.Ended, controller.playbackState.value.phase)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `skipNext uses selectInWindow when next already preloaded`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = false)
        advanceTimeBy(1L)
        engine.windowOps.clear()

        controller.skipNext()
        advanceTimeBy(1L)

        assertEquals(
            listOf(WindowOp.SelectInWindow(appItemId = "t2", autoPlay = true)),
            engine.windowOps,
        )

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `skipNext falls back to setQueueWindow when target is outside window`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2"), item("t3")), autoPlay = false)
        advanceTimeBy(1L)
        engine.windowItemIds.clear()
        engine.windowOps.clear()

        controller.skipNext()
        advanceTimeBy(1L)

        assertEquals(
            listOf(
                WindowOp.SelectInWindow(appItemId = "t2", autoPlay = true),
                WindowOp.SetWindow(
                    current = AudioTrackRequest("t2", "http://host/t2.mp3"),
                    next = AudioTrackRequest("t3", "http://host/t3.mp3"),
                    autoPlay = true,
                ),
            ),
            engine.windowOps,
        )

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `watchdog fires for setQueueWindow path when loading stalls`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val timeoutMs = 5_000L
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this, loadTimeoutMs = timeoutMs)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        advanceTimeBy(1L)
        advanceTimeBy(timeoutMs + 1L)

        assertEquals(PlaybackPhase.Failed, controller.playbackState.value.phase)
        assertTrue(controller.playbackState.value.playbackError is PlaybackError.StreamFailed)

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `watchdog is not armed when skipNext succeeds via selectInWindow`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val timeoutMs = 5_000L
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this, loadTimeoutMs = timeoutMs)

        controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = false)
        advanceTimeBy(1L)
        engine.windowOps.clear()

        controller.skipNext()
        advanceTimeBy(1L)
        advanceTimeBy(timeoutMs + 1L)

        assertFalse(controller.playbackState.value.phase == PlaybackPhase.Failed)
        assertTrue(engine.windowOps.contains(WindowOp.SelectInWindow("t2", true)))

        controller.shutdown()
        advanceUntilIdle()
    }

    @Test
    fun `pause transitions active loading phase to Paused`() = runTest {
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

    @Test
    fun `shutdown resets state and stops engine`() = runTest {
        val engine = FakeAudioPlaybackEngine()
        val controller = PlaybackQueueController(engine, StaticUrlResolver(), scope = this)

        controller.replaceQueue(queue = listOf(item("t1")), autoPlay = false)
        advanceTimeBy(1L)

        controller.shutdown()
        advanceUntilIdle()

        assertEquals(PlaybackPhase.Idle, controller.playbackState.value.phase)
        assertTrue(controller.playbackState.value.queue.isEmpty())
        assertEquals(1, engine.stopCallCount)
    }
}

private fun item(id: String, durationMs: Long = 180_000L) = PlaybackQueueItem(
    id = id,
    trackId = YandexTrackId(id),
    source = PlayableSource.Remote(MusicProviderId.YandexMusic),
    title = "Track $id",
    subtitle = "Artist",
    durationMs = durationMs,
)

private class StaticUrlResolver : PlayableUrlResolver {
    override suspend fun getPlayableUrl(item: PlaybackQueueItem): String = "http://host/${item.id}.mp3"
}

private sealed interface WindowOp {
    data class SetWindow(
        val current: AudioTrackRequest,
        val next: AudioTrackRequest?,
        val autoPlay: Boolean,
    ) : WindowOp

    data class AppendNext(val request: AudioTrackRequest) : WindowOp

    data class SelectInWindow(val appItemId: String, val autoPlay: Boolean) : WindowOp

    data class PruneWindow(val keepAppItemIds: Set<String>) : WindowOp
}

private class FakeAudioPlaybackEngine : AudioPlaybackEngine {

    private val _engineState = MutableStateFlow(AudioEngineState())
    private val _events = MutableSharedFlow<AudioEngineEvent>(extraBufferCapacity = 16)

    override val engineState: StateFlow<AudioEngineState> = _engineState.asStateFlow()
    override val events: SharedFlow<AudioEngineEvent> = _events.asSharedFlow()

    val windowOps = mutableListOf<WindowOp>()
    val windowItemIds = linkedSetOf<String>()
    val seekRequests = mutableListOf<Long>()
    var playCallCount = 0
    var pauseCallCount = 0
    var stopCallCount = 0

    override fun play() {
        playCallCount++
        _engineState.value = _engineState.value.copy(isPlaying = true)
    }

    override fun pause() {
        pauseCallCount++
        _engineState.value = _engineState.value.copy(isPlaying = false)
    }

    override suspend fun seekTo(positionMs: Long): Boolean {
        seekRequests += positionMs
        _engineState.value = _engineState.value.copy(currentPositionMs = positionMs)
        return true
    }

    override fun setQueueWindow(
        current: AudioTrackRequest,
        next: AudioTrackRequest?,
        autoPlay: Boolean,
    ) {
        windowOps += WindowOp.SetWindow(current, next, autoPlay)
        windowItemIds.clear()
        windowItemIds += current.id
        next?.let { windowItemIds += it.id }
        _engineState.value = AudioEngineState(
            status = AudioEngineStatus.ReadyToPlay,
            currentItemId = current.id,
            isPlaying = false,
        )
    }

    override fun appendNext(next: AudioTrackRequest) {
        windowOps += WindowOp.AppendNext(next)
        windowItemIds += next.id
    }

    override suspend fun selectInWindow(appItemId: String, autoPlay: Boolean): Boolean {
        windowOps += WindowOp.SelectInWindow(appItemId, autoPlay)
        if (appItemId !in windowItemIds) return false

        _engineState.value = _engineState.value.copy(
            currentItemId = appItemId,
            currentPositionMs = 0L,
            isPlaying = autoPlay,
        )
        return true
    }

    override fun pruneWindow(keepAppItemIds: Set<String>) {
        windowOps += WindowOp.PruneWindow(keepAppItemIds)
        windowItemIds.retainAll(keepAppItemIds)
    }

    override fun stop() {
        stopCallCount++
        windowItemIds.clear()
        _engineState.value = AudioEngineState()
    }

    suspend fun emitEvent(event: AudioEngineEvent) {
        _events.emit(event)
    }
}
