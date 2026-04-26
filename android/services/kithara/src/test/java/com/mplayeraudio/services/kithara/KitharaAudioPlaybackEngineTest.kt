package com.mplayeraudio.services.kithara

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KitharaAudioPlaybackEngineTest {

    // ── autoPlay ─────────────────────────────────────────────────────────────

    @Test
    fun `loadTrack autoPlay=true calls play when item becomes ReadyToPlay`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"), autoPlay = true)
        runCurrent()

        fakePlayer.lastItem!!.setSnapshot(EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay))
        runCurrent()

        assertEquals(1, fakePlayer.playCallCount)
        shutdownEngine(engine)
    }

    @Test
    fun `loadTrack autoPlay=false does not call play when item becomes ReadyToPlay`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"), autoPlay = false)
        runCurrent()

        fakePlayer.lastItem!!.setSnapshot(EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay))
        runCurrent()

        assertEquals(0, fakePlayer.playCallCount)
        shutdownEngine(engine)
    }

    @Test
    fun `play is triggered only once even if ReadyToPlay fires multiple times`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"), autoPlay = true)
        runCurrent()

        val item = fakePlayer.lastItem!!
        item.setSnapshot(EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay))
        runCurrent()
        // Reset and re-emit ReadyToPlay (e.g. after seek)
        item.setSnapshot(EngineItemSnapshot(status = EngineItemStatus.Unknown))
        item.setSnapshot(EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay))
        runCurrent()

        assertEquals("play() must be called once", 1, fakePlayer.playCallCount)
        shutdownEngine(engine)
    }

    // ── ItemFailed ────────────────────────────────────────────────────────────

    @Test
    fun `item Failed emits ItemFailed with LoadFailed error`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"))
        runCurrent()

        fakePlayer.lastItem!!.setSnapshot(
            EngineItemSnapshot(
                status = EngineItemStatus.Failed,
                error = AudioEngineError.LoadFailed("Network error"),
            ),
        )
        runCurrent()

        val event = events.filterIsInstance<AudioEngineEvent.ItemFailed>().singleOrNull()
        assertNotNull("Expected ItemFailed event", event)
        assertEquals("t1", event!!.itemId)
        assertEquals(AudioEngineError.LoadFailed("Network error"), event.reason)
        shutdownEngine(engine)
    }

    @Test
    fun `mid-playback item Failed also emits ItemFailed`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "t2", url = "http://host/2.mp3"), autoPlay = true)
        runCurrent()

        val item = fakePlayer.lastItem!!
        item.setSnapshot(EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay))
        runCurrent()

        // Mid-playback failure
        item.setSnapshot(
            EngineItemSnapshot(
                status = EngineItemStatus.Failed,
                error = AudioEngineError.StreamFailed("Stream lost"),
            ),
        )
        runCurrent()

        val event = events.filterIsInstance<AudioEngineEvent.ItemFailed>().singleOrNull()
        assertNotNull("Expected ItemFailed event after mid-playback failure", event)
        assertEquals("t2", event!!.itemId)
        assertTrue(event.reason is AudioEngineError.StreamFailed)
        shutdownEngine(engine)
    }

    @Test
    fun `ItemFailed is emitted only once when Failed state repeats`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"))
        runCurrent()

        val failedSnapshot = EngineItemSnapshot(
            status = EngineItemStatus.Failed,
            error = AudioEngineError.LoadFailed("Error"),
        )
        fakePlayer.lastItem!!.setSnapshot(failedSnapshot)
        runCurrent()
        // Re-emit same state
        fakePlayer.lastItem!!.setSnapshot(EngineItemSnapshot())
        fakePlayer.lastItem!!.setSnapshot(failedSnapshot)
        runCurrent()

        assertEquals(1, events.filterIsInstance<AudioEngineEvent.ItemFailed>().size)
        shutdownEngine(engine)
    }

    // ── DurationDiscovered ────────────────────────────────────────────────────

    @Test
    fun `duration non-null emits DurationDiscovered and updates engineState`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "m3u8", url = "http://host/stream.m3u8"))
        runCurrent()

        fakePlayer.lastItem!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay, durationMs = 300_000L),
        )
        runCurrent()

        val event = events.filterIsInstance<AudioEngineEvent.DurationDiscovered>().firstOrNull()
        assertNotNull("Expected DurationDiscovered event", event)
        assertEquals("m3u8", event!!.itemId)
        assertEquals(300_000L, event.durationMs)
        assertEquals(300_000L, engine.engineState.value.durationMs)
        shutdownEngine(engine)
    }

    @Test
    fun `each distinct duration change emits a new DurationDiscovered`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "live", url = "http://host/live.m3u8"))
        runCurrent()

        val item = fakePlayer.lastItem!!
        item.setSnapshot(EngineItemSnapshot(durationMs = 100_000L))
        runCurrent()
        item.setSnapshot(EngineItemSnapshot(durationMs = 200_000L))
        runCurrent()

        val durationEvents = events.filterIsInstance<AudioEngineEvent.DurationDiscovered>()
        assertEquals(2, durationEvents.size)
        assertEquals(100_000L, durationEvents[0].durationMs)
        assertEquals(200_000L, durationEvents[1].durationMs)
        shutdownEngine(engine)
    }

    @Test
    fun `same duration value does not emit a second DurationDiscovered`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"))
        runCurrent()

        val item = fakePlayer.lastItem!!
        item.setSnapshot(EngineItemSnapshot(durationMs = 180_000L))
        runCurrent()
        item.setSnapshot(EngineItemSnapshot(durationMs = 180_000L))
        runCurrent()

        assertEquals(1, events.filterIsInstance<AudioEngineEvent.DurationDiscovered>().size)
        shutdownEngine(engine)
    }

    // ── EngineFailed ──────────────────────────────────────────────────────────

    @Test
    fun `player status Failed emits EngineFailed event`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        fakePlayer.setPlayerSnapshot(
            EnginePlayerSnapshot(
                status = AudioEngineStatus.Failed,
                error = AudioEngineError.EngineCrashed("crash"),
            ),
        )
        runCurrent()

        val event = events.filterIsInstance<AudioEngineEvent.EngineFailed>().firstOrNull()
        assertNotNull("Expected EngineFailed event", event)
        assertEquals(AudioEngineError.EngineCrashed("crash"), event!!.reason)
        shutdownEngine(engine)
    }

    @Test
    fun `EngineFailed is emitted only once per consecutive Failed status`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        val failedSnapshot = EnginePlayerSnapshot(
            status = AudioEngineStatus.Failed,
            error = AudioEngineError.EngineCrashed("crash"),
        )
        fakePlayer.setPlayerSnapshot(failedSnapshot)
        runCurrent()
        fakePlayer.setPlayerSnapshot(failedSnapshot)
        runCurrent()

        assertEquals(1, events.filterIsInstance<AudioEngineEvent.EngineFailed>().size)
        shutdownEngine(engine)
    }

    // ── engineState reflects player snapshot ──────────────────────────────────

    @Test
    fun `engineState reflects position, buffered and isPlaying from player snapshot`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        fakePlayer.setPlayerSnapshot(
            EnginePlayerSnapshot(
                currentPositionMs = 45_000L,
                bufferedPositionMs = 60_000L,
                rate = 1.0f,
                status = AudioEngineStatus.ReadyToPlay,
            ),
        )
        runCurrent()

        val state = engine.engineState.value
        assertEquals(45_000L, state.currentPositionMs)
        assertEquals(60_000L, state.bufferedPositionMs)
        assertTrue(state.isPlaying)
        assertEquals(AudioEngineStatus.ReadyToPlay, state.status)
        shutdownEngine(engine)
    }

    @Test
    fun `engineState currentItemId is set immediately after loadTrack`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        engine.loadTrack(AudioTrackRequest(id = "my-track", url = "http://host/1.mp3"))
        runCurrent()

        assertEquals("my-track", engine.engineState.value.currentItemId)
        shutdownEngine(engine)
    }

    // ── stop / cancel ─────────────────────────────────────────────────────────

    @Test
    fun `stop cancels item observation — late Failed does not emit ItemFailed`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"))
        runCurrent()

        val item = fakePlayer.lastItem!!
        engine.stop()
        runCurrent()

        item.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.Failed, error = AudioEngineError.LoadFailed("late")),
        )
        runCurrent()

        assertFalse(
            "ItemFailed must not fire after stop()",
            events.any { it is AudioEngineEvent.ItemFailed },
        )
        shutdownEngine(engine)
    }

    @Test
    fun `second loadTrack cancels observation of first item`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"))
        runCurrent()
        val firstItem = fakePlayer.lastItem!!

        engine.loadTrack(AudioTrackRequest(id = "t2", url = "http://host/2.mp3"))
        runCurrent()

        firstItem.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.Failed, error = AudioEngineError.LoadFailed("stale")),
        )
        runCurrent()

        assertFalse(
            "ItemFailed for old item must not fire after new loadTrack",
            events.any { it is AudioEngineEvent.ItemFailed && it.itemId == "t1" },
        )
        shutdownEngine(engine)
    }

    @Test
    fun `stop resets engineState to empty`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"))
        runCurrent()

        engine.stop()
        runCurrent()

        val state = engine.engineState.value
        assertNull(state.currentItemId)
        assertEquals(0L, state.currentPositionMs)
        assertFalse(state.isPlaying)
        assertEquals(AudioEngineStatus.Idle, state.status)
        shutdownEngine(engine)
    }

    // ── PlayerEvents ─────────────────────────────────────────────────────────

    @Test
    fun `PlayedToEnd player event is forwarded to engine events`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "app-id", url = "http://host/1.mp3"))
        runCurrent()

        val kitharaId = fakePlayer.lastItem!!.kitharaId
        fakePlayer.emitPlayerEvent(EnginePlayerEvent.PlayedToEnd(kitharaId))
        runCurrent()

        val event = events.filterIsInstance<AudioEngineEvent.PlayedToEnd>().singleOrNull()
        assertNotNull(event)
        assertEquals("app-id", event!!.itemId)
        shutdownEngine(engine)
    }

    @Test
    fun `PlayedToEnd for unmapped kithara item is ignored`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"))
        runCurrent()

        fakePlayer.emitPlayerEvent(EnginePlayerEvent.PlayedToEnd("stale-kithara-id"))
        runCurrent()

        assertTrue(events.filterIsInstance<AudioEngineEvent.PlayedToEnd>().isEmpty())

        shutdownEngine(engine)
    }

    @Test
    fun `PlayedToEnd after replace resolves to current app item only`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)

        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"))
        runCurrent()
        val firstKitharaId = fakePlayer.lastItem!!.kitharaId

        engine.loadTrack(AudioTrackRequest(id = "t2", url = "http://host/2.mp3"))
        runCurrent()
        val secondKitharaId = fakePlayer.lastItem!!.kitharaId

        fakePlayer.emitPlayerEvent(EnginePlayerEvent.PlayedToEnd(firstKitharaId))
        runCurrent()
        assertTrue(events.filterIsInstance<AudioEngineEvent.PlayedToEnd>().isEmpty())

        fakePlayer.emitPlayerEvent(EnginePlayerEvent.PlayedToEnd(secondKitharaId))
        runCurrent()

        val playedToEndEvents = events.filterIsInstance<AudioEngineEvent.PlayedToEnd>()
        assertEquals(1, playedToEndEvents.size)
        assertEquals("t2", playedToEndEvents.single().itemId)

        shutdownEngine(engine)
    }

    @Test
    fun `CurrentItemChanged player event is forwarded with mapped appItemId`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        val events = engine.collectEvents(this)
        engine.loadTrack(AudioTrackRequest(id = "app-id", url = "http://host/1.mp3"))
        runCurrent()

        val kitharaId = fakePlayer.lastItem!!.kitharaId
        fakePlayer.emitPlayerEvent(EnginePlayerEvent.CurrentItemChanged(kitharaId))
        runCurrent()

        val event = events.filterIsInstance<AudioEngineEvent.CurrentItemChanged>().lastOrNull()
        assertNotNull(event)
        assertEquals("app-id", event!!.itemId)
        shutdownEngine(engine)
    }

    @Test
    fun `shutdown cancels internal childScope so advanceUntilIdle is safe`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        engine.loadTrack(AudioTrackRequest(id = "t1", url = "http://host/1.mp3"))
        runCurrent()

        shutdownEngine(engine)
        assertNull(engine.engineState.value.currentItemId)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Test helpers
// ────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
private fun KitharaAudioPlaybackEngine.collectEvents(
    scope: TestScope,
): MutableList<AudioEngineEvent> {
    val list = mutableListOf<AudioEngineEvent>()
    scope.backgroundScope.launch { events.collect { list += it } }
    scope.runCurrent()
    return list
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.shutdownEngine(engine: KitharaAudioPlaybackEngine) {
    engine.shutdown()
    advanceUntilIdle()
}

// ────────────────────────────────────────────────────────────────────────────
// Fakes — used only in tests; no com.kithara.* dependencies
// ────────────────────────────────────────────────────────────────────────────

private class FakeKitharaPlayerHandle : KitharaPlayerHandle {

    private val _snapshots = MutableStateFlow(EnginePlayerSnapshot())
    private val _events = MutableSharedFlow<EnginePlayerEvent>(extraBufferCapacity = 16)

    override val snapshots: StateFlow<EnginePlayerSnapshot> = _snapshots.asStateFlow()
    override val events: Flow<EnginePlayerEvent> = _events.asSharedFlow()

    val createdItems = mutableListOf<FakeKitharaItemHandle>()
    val lastItem: FakeKitharaItemHandle? get() = createdItems.lastOrNull()

    var playCallCount = 0
    var pauseCallCount = 0
    var removeAllItemsCallCount = 0

    override fun play() { playCallCount++ }
    override fun pause() { pauseCallCount++ }
    override fun seek(seconds: Double, callback: (Boolean) -> Unit) = callback(true)
    override fun removeAllItems() { removeAllItemsCallCount++ }

    override fun createAndLoadItem(url: String): KitharaItemHandle {
        val item = FakeKitharaItemHandle(url)
        createdItems += item
        return item
    }

    fun setPlayerSnapshot(snapshot: EnginePlayerSnapshot) { _snapshots.value = snapshot }
    suspend fun emitPlayerEvent(event: EnginePlayerEvent) = _events.emit(event)
}

private class FakeKitharaItemHandle(val url: String) : KitharaItemHandle {

    private val _snapshots = MutableStateFlow(EngineItemSnapshot())

    override val kitharaId: String = "fake:${url.hashCode()}"
    override val snapshots: StateFlow<EngineItemSnapshot> = _snapshots.asStateFlow()

    fun setSnapshot(snapshot: EngineItemSnapshot) { _snapshots.value = snapshot }
}
