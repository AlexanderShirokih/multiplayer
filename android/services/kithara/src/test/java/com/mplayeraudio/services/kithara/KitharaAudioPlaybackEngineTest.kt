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

    @Test
    fun `setQueueWindow inserts both current and next and autoPlay plays once`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        engine.setQueueWindow(
            current = AudioTrackRequest(id = "t1", url = "http://host/1.mp3"),
            next = AudioTrackRequest(id = "t2", url = "http://host/2.mp3"),
            autoPlay = true,
        )
        runCurrent()

        assertEquals(listOf("http://host/1.mp3", "http://host/2.mp3"), fakePlayer.insertedUrls)
        assertEquals("t1", engine.engineState.value.currentItemId)

        fakePlayer.item("http://host/2.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay),
        )
        runCurrent()
        assertEquals(0, fakePlayer.playCallCount)

        fakePlayer.item("http://host/1.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay),
        )
        runCurrent()

        assertEquals(1, fakePlayer.playCallCount)
        shutdownEngine(engine)
    }

    @Test
    fun `appendNext does not call play and keeps current item unchanged`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        engine.setQueueWindow(
            current = AudioTrackRequest(id = "t1", url = "http://host/1.mp3"),
            next = null,
            autoPlay = false,
        )
        runCurrent()
        engine.appendNext(AudioTrackRequest(id = "t2", url = "http://host/2.mp3"))
        runCurrent()

        fakePlayer.item("http://host/2.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay),
        )
        runCurrent()

        assertEquals(0, fakePlayer.playCallCount)
        assertEquals("t1", engine.engineState.value.currentItemId)
        shutdownEngine(engine)
    }

    @Test
    fun `failed status on next item drops it from window without emitting ItemFailed`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)
        val events = engine.collectEvents(this)

        engine.setQueueWindow(
            current = AudioTrackRequest(id = "t1", url = "http://host/1.mp3"),
            next = AudioTrackRequest(id = "t2", url = "http://host/2.mp3"),
            autoPlay = false,
        )
        runCurrent()
        val nextKitharaId = fakePlayer.itemId("http://host/2.mp3")

        fakePlayer.item("http://host/2.mp3")!!.setSnapshot(
            EngineItemSnapshot(
                status = EngineItemStatus.Failed,
                error = AudioEngineError.LoadFailed("boom"),
            ),
        )
        runCurrent()
        advanceUntilIdle()

        assertTrue(events.filterIsInstance<AudioEngineEvent.ItemFailed>().isEmpty())
        assertTrue(fakePlayer.removedItemIds.contains(nextKitharaId))
        shutdownEngine(engine)
    }

    @Test
    fun `failed status on current item emits ItemFailed`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)
        val events = engine.collectEvents(this)

        engine.setQueueWindow(
            current = AudioTrackRequest(id = "t1", url = "http://host/1.mp3"),
            next = AudioTrackRequest(id = "t2", url = "http://host/2.mp3"),
            autoPlay = false,
        )
        runCurrent()

        fakePlayer.item("http://host/1.mp3")!!.setSnapshot(
            EngineItemSnapshot(
                status = EngineItemStatus.Failed,
                error = AudioEngineError.StreamFailed("lost"),
            ),
        )
        runCurrent()

        val event = events.filterIsInstance<AudioEngineEvent.ItemFailed>().singleOrNull()
        assertNotNull(event)
        assertEquals("t1", event!!.itemId)
        assertTrue(event.reason is AudioEngineError.StreamFailed)
        shutdownEngine(engine)
    }

    @Test
    fun `selectInWindow selects existing next item and can trigger play`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)

        engine.setQueueWindow(
            current = AudioTrackRequest(id = "t1", url = "http://host/1.mp3"),
            next = AudioTrackRequest(id = "t2", url = "http://host/2.mp3"),
            autoPlay = false,
        )
        runCurrent()

        fakePlayer.item("http://host/2.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay, durationMs = 42_000L),
        )
        runCurrent()

        val selected = engine.selectInWindow(appItemId = "t2", autoPlay = true)
        runCurrent()

        assertTrue(selected)
        assertEquals(listOf(fakePlayer.itemId("http://host/2.mp3")), fakePlayer.selectedItemIds)
        assertEquals("t2", engine.engineState.value.currentItemId)
        assertEquals(1, fakePlayer.playCallCount)
        assertEquals(42_000L, engine.engineState.value.durationMs)
        shutdownEngine(engine)
    }

    @Test
    fun `CurrentItemChanged and PlayedToEnd are mapped for items in current window`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)
        val events = engine.collectEvents(this)

        engine.setQueueWindow(
            current = AudioTrackRequest(id = "t1", url = "http://host/1.mp3"),
            next = AudioTrackRequest(id = "t2", url = "http://host/2.mp3"),
            autoPlay = false,
        )
        runCurrent()

        val nextKitharaId = fakePlayer.itemId("http://host/2.mp3")
        fakePlayer.emitPlayerEvent(EnginePlayerEvent.CurrentItemChanged(nextKitharaId))
        fakePlayer.emitPlayerEvent(EnginePlayerEvent.PlayedToEnd(nextKitharaId))
        runCurrent()

        val currentChanged = events.filterIsInstance<AudioEngineEvent.CurrentItemChanged>().lastOrNull()
        val playedToEnd = events.filterIsInstance<AudioEngineEvent.PlayedToEnd>().singleOrNull()

        assertEquals("t2", currentChanged?.itemId)
        assertEquals("t2", playedToEnd?.itemId)
        assertEquals("t2", engine.engineState.value.currentItemId)
        shutdownEngine(engine)
    }

    @Test
    fun `stop clears window and late failures are ignored`() = runTest {
        val fakePlayer = FakeKitharaPlayerHandle()
        val engine = KitharaAudioPlaybackEngine(scope = this, player = fakePlayer)
        val events = engine.collectEvents(this)

        engine.setQueueWindow(
            current = AudioTrackRequest(id = "t1", url = "http://host/1.mp3"),
            next = null,
            autoPlay = false,
        )
        runCurrent()

        val currentItem = fakePlayer.item("http://host/1.mp3")!!
        engine.stop()
        runCurrent()

        currentItem.setSnapshot(
            EngineItemSnapshot(
                status = EngineItemStatus.Failed,
                error = AudioEngineError.LoadFailed("late"),
            ),
        )
        runCurrent()

        assertTrue(events.filterIsInstance<AudioEngineEvent.ItemFailed>().isEmpty())
        assertNull(engine.engineState.value.currentItemId)
        assertEquals(2, fakePlayer.removeAllItemsCallCount)
        shutdownEngine(engine)
    }
}

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

private class FakeKitharaPlayerHandle : KitharaPlayerHandle {

    private val _snapshots = MutableStateFlow(EnginePlayerSnapshot())
    private val _events = MutableSharedFlow<EnginePlayerEvent>(extraBufferCapacity = 16)
    private val itemsByUrl = linkedMapOf<String, FakeKitharaItemHandle>()
    private val itemsById = linkedMapOf<String, FakeKitharaItemHandle>()

    override val snapshots: StateFlow<EnginePlayerSnapshot> = _snapshots.asStateFlow()
    override val events: Flow<EnginePlayerEvent> = _events.asSharedFlow()

    val insertedUrls = mutableListOf<String>()
    val selectedItemIds = mutableListOf<String>()
    val removedItemIds = mutableListOf<String>()
    var playCallCount = 0
    var pauseCallCount = 0
    var removeAllItemsCallCount = 0

    override fun play() {
        playCallCount++
        _snapshots.value = _snapshots.value.copy(rate = 1f)
    }

    override fun pause() {
        pauseCallCount++
        _snapshots.value = _snapshots.value.copy(rate = 0f)
    }

    override fun seek(seconds: Double, callback: (Boolean) -> Unit) = callback(true)

    override fun insertItem(url: String): KitharaItemHandle {
        insertedUrls += url
        val item = FakeKitharaItemHandle(url)
        itemsByUrl[url] = item
        itemsById[item.kitharaId] = item
        return item
    }

    override fun selectItem(kitharaId: String) {
        selectedItemIds += kitharaId
        _snapshots.value = _snapshots.value.copy(currentKitharaItemId = kitharaId)
    }

    override fun removeItem(kitharaId: String) {
        removedItemIds += kitharaId
        val item = itemsById.remove(kitharaId) ?: return
        itemsByUrl.remove(item.url)
    }

    override fun removeAllItems() {
        removeAllItemsCallCount++
        itemsById.clear()
        itemsByUrl.clear()
        _snapshots.value = EnginePlayerSnapshot()
    }

    fun item(url: String): FakeKitharaItemHandle? = itemsByUrl[url]

    fun itemId(url: String): String = requireNotNull(item(url)).kitharaId

    suspend fun emitPlayerEvent(event: EnginePlayerEvent) {
        _events.emit(event)
    }
}

private class FakeKitharaItemHandle(val url: String) : KitharaItemHandle {

    private val _snapshots = MutableStateFlow(EngineItemSnapshot())

    override val kitharaId: String = "fake:${url.hashCode()}"
    override val snapshots: StateFlow<EngineItemSnapshot> = _snapshots.asStateFlow()

    fun setSnapshot(snapshot: EngineItemSnapshot) {
        _snapshots.value = snapshot
    }
}
