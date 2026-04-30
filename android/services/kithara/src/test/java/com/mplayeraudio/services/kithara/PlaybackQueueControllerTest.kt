package com.mplayeraudio.services.kithara

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlayableUrlResolver
import com.mplayeraudio.core.player.PlaybackError
import com.mplayeraudio.core.player.PlaybackPhase
import com.mplayeraudio.core.player.PlaybackQueueItem
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
    fun `replaceQueue with autoPlay loads first item and enters Loading`() = runTest {
        val ctx = controllerTestContext(this)

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = true)
        }
        runCurrent()

        assertEquals(listOf("http://host/t1.mp3"), ctx.player.insertedUrls)
        assertEquals(PlaybackPhase.Loading, ctx.controller.playbackState.value.phase)
        assertEquals(0, ctx.controller.playbackState.value.currentIndex)
        shutdown(ctx, this)
    }

    @Test
    fun `ready item triggers play for autoPlay request`() = runTest {
        val ctx = controllerTestContext(this)

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        }
        runCurrent()
        ctx.player.item("http://host/t1.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay, durationMs = 42_000L),
        )
        advanceUntilIdle()

        assertEquals(1, ctx.player.playCallCount)
        assertEquals(PlaybackPhase.Playing, ctx.controller.playbackState.value.phase)
        assertEquals(42_000L, ctx.controller.playbackState.value.currentDurationMs)
        shutdown(ctx, this)
    }

    @Test
    fun `autoPlay waits for current item selection before playing`() = runTest {
        val ctx = controllerTestContext(this)
        ctx.player.blockSelection()

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        }
        runCurrent()

        ctx.player.item("http://host/t1.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay, durationMs = 42_000L),
        )
        runCurrent()

        assertEquals(0, ctx.player.playCallCount)
        assertEquals(0, ctx.player.selectCallCount)

        ctx.player.releaseBlockedSelection()
        advanceUntilIdle()

        assertEquals(1, ctx.player.selectCallCount)
        assertEquals(1, ctx.player.playCallCount)
        assertEquals(PlaybackPhase.Playing, ctx.controller.playbackState.value.phase)
        shutdown(ctx, this)
    }

    @Test
    fun `replaceQueue without explicit target only syncs queue`() = runTest {
        val ctx = controllerTestContext(this)

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1")), autoPlay = false)
        }
        runCurrent()

        assertTrue(ctx.player.insertedUrls.isEmpty())
        assertEquals(PlaybackPhase.Idle, ctx.controller.playbackState.value.phase)
        assertEquals(null, ctx.controller.playbackState.value.currentIndex)
        assertEquals(0, ctx.player.selectCallCount)
        shutdown(ctx, this)
    }

    @Test
    fun `replaceQueue with explicit start index waits for inserted item readiness before selecting`() = runTest {
        val ctx = controllerTestContext(this)

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1")), startIndex = 0, autoPlay = false)
        }
        runCurrent()

        assertEquals(PlaybackPhase.Loading, ctx.controller.playbackState.value.phase)
        assertEquals(0, ctx.player.selectCallCount)

        ctx.player.item("http://host/t1.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay),
        )
        advanceUntilIdle()

        assertEquals(1, ctx.player.selectCallCount)
        assertEquals(PlaybackPhase.Paused, ctx.controller.playbackState.value.phase)
        shutdown(ctx, this)
    }

    @Test
    fun `replaceQueue preserving loaded current item does not reload`() = runTest {
        val ctx = controllerTestContext(this)

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1"), item("t2")), startIndex = 0, autoPlay = false)
        }
        runCurrent()
        ctx.player.item("http://host/t1.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay),
        )
        advanceUntilIdle()
        ctx.player.resetRecordedOps()

        ctx.controller.replaceQueue(queue = listOf(item("t1"), item("t3")), autoPlay = false)
        runCurrent()

        assertTrue(ctx.player.insertedUrls.isEmpty())
        assertEquals(0, ctx.player.removeAllItemsCallCount)
        shutdown(ctx, this)
    }

    @Test
    fun `played to end loads next queue item`() = runTest {
        val ctx = controllerTestContext(this)

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = true)
        }
        runCurrent()
        val currentHandle = ctx.player.item("http://host/t1.mp3")!!
        currentHandle.setSnapshot(EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay))
        advanceUntilIdle()
        ctx.player.resetRecordedOps()

        ctx.player.emitPlayerEvent(KitharaPlayerEvent.PlayedToEnd(currentHandle.kitharaId))
        runCurrent()

        assertEquals(listOf("http://host/t2.mp3"), ctx.player.insertedUrls)
        assertEquals(1, ctx.controller.playbackState.value.currentIndex)
        assertEquals(PlaybackPhase.Loading, ctx.controller.playbackState.value.phase)
        shutdown(ctx, this)
    }

    @Test
    fun `watchdog marks playback as failed when item never becomes ready`() = runTest {
        val ctx = controllerTestContext(this, loadTimeoutMs = 5_000L)

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1")), autoPlay = true)
        }
        runCurrent()
        advanceTimeBy(5_001L)
        advanceUntilIdle()

        assertEquals(PlaybackPhase.Failed, ctx.controller.playbackState.value.phase)
        assertTrue(ctx.controller.playbackState.value.playbackError is PlaybackError.StreamFailed)
        shutdown(ctx, this)
    }

    @Test
    fun `seek publishes confirmed position after callback`() = runTest {
        val ctx = controllerTestContext(this)

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1")), startIndex = 0, autoPlay = false)
        }
        runCurrent()
        ctx.player.item("http://host/t1.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay, durationMs = 120_000L),
        )
        advanceUntilIdle()
        ctx.player.updatePosition(15_000L)
        ctx.player.blockSeek()

        val seekJob = backgroundScope.launch {
            ctx.controller.seekTo(90_000L)
        }
        runCurrent()

        assertEquals(15_000L, ctx.controller.playbackState.value.currentPositionMs)
        ctx.player.finishBlockedSeek(success = true, positionMs = 90_000L)
        advanceUntilIdle()

        assertEquals(90_000L, ctx.controller.playbackState.value.currentPositionMs)
        assertFalse(seekJob.isCancelled)
        seekJob.join()
        shutdown(ctx, this)
    }

    @Test
    fun `late playing snapshot from previous item must not cancel autoplay for newly selected item`() = runTest {
        val ctx = controllerTestContext(this)

        backgroundScope.launch {
            ctx.controller.replaceQueue(queue = listOf(item("t1"), item("t2")), autoPlay = true)
        }
        runCurrent()
        ctx.player.item("http://host/t1.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay, durationMs = 42_000L),
        )
        advanceUntilIdle()
        assertEquals(1, ctx.player.playCallCount)

        ctx.player.blockSelection()
        val switchJob = backgroundScope.launch {
            ctx.controller.playTrack(1)
        }
        runCurrent()

        ctx.player.updatePosition(positionMs = 7_000L, isPlaying = true)
        runCurrent()

        ctx.player.item("http://host/t2.mp3")!!.setSnapshot(
            EngineItemSnapshot(status = EngineItemStatus.ReadyToPlay, durationMs = 50_000L),
        )
        runCurrent()
        ctx.player.releaseBlockedSelection()
        advanceUntilIdle()

        assertEquals(2, ctx.player.selectCallCount)
        assertEquals(2, ctx.player.playCallCount)
        assertEquals(PlaybackPhase.Playing, ctx.controller.playbackState.value.phase)
        assertEquals("t2", ctx.controller.playbackState.value.currentItem?.id)
        switchJob.join()
        shutdown(ctx, this)
    }
}

private data class ControllerTestContext(
    val controller: PlaybackQueueController,
    val player: MockKitharaPlayerHarness,
)

private fun controllerTestContext(
    scope: TestScope,
    resolver: PlayableUrlResolver = StaticUrlResolver(),
    loadTimeoutMs: Long = 30_000L,
): ControllerTestContext {
    val playerHarness = MockKitharaPlayerHarness()
    val controller = PlaybackQueueController(
        player = playerHarness.wrapper,
        urlResolver = resolver,
        scope = scope,
        loadTimeoutMs = loadTimeoutMs,
    )
    return ControllerTestContext(controller = controller, player = playerHarness)
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun shutdown(
    ctx: ControllerTestContext,
    scope: TestScope,
) {
    ctx.controller.shutdown()
    scope.advanceUntilIdle()
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

private class MockKitharaPlayerHarness {
    private val snapshotState = MutableStateFlow(EnginePlayerSnapshot())
    private val eventFlow = MutableSharedFlow<KitharaPlayerEvent>(extraBufferCapacity = 16)
    private val itemsByUrl = linkedMapOf<String, TestKitharaItemHandle>()
    private val itemsById = linkedMapOf<String, TestKitharaItemHandle>()
    private var pendingSeekCallback: ((Boolean) -> Unit)? = null
    private var nextItemOrdinal = 0
    private var selectionBlocked = false
    private var selectionGate = CompletableDeferred<Unit>()

    val wrapper: KitharaPlayerWrapper = mockk(relaxed = true)
    val insertedUrls = mutableListOf<String>()
    var playCallCount = 0
    var removeAllItemsCallCount = 0
    var selectCallCount = 0
    private var seekBlocked = false

    init {
        every { wrapper.snapshots } returns snapshotState.asStateFlow()
        every { wrapper.events } returns eventFlow.asSharedFlow()
        every { wrapper.play() } answers {
            playCallCount += 1
            snapshotState.value = snapshotState.value.copy(rate = 1f, status = AudioEngineStatus.ReadyToPlay)
        }
        every { wrapper.pause() } answers {
            snapshotState.value = snapshotState.value.copy(rate = 0f)
        }
        coEvery { wrapper.seek(any()) } coAnswers {
            val positionMs = (firstArg<Double>() * 1000).toLong()
            if (seekBlocked) {
                suspendSeek(positionMs)
            } else {
                updatePosition(positionMs)
                true
            }
        }
        every { wrapper.insertItem(any()) } answers {
            val url = firstArg<String>()
            insertedUrls += url
            val item = TestKitharaItemHandle(
                url = url,
                kitharaId = "mock:${nextItemOrdinal++}:${url.hashCode()}",
                emitEvent = eventFlow::tryEmit,
            )
            itemsByUrl[url] = item
            itemsById[item.kitharaId] = item
            item
        }
        coEvery { wrapper.selectItem(any()) } coAnswers {
            val kitharaId = firstArg<String>()
            val item = itemsById[kitharaId] ?: error("Missing test item: $kitharaId")
            item.awaitSelectionReady()
            if (selectionBlocked) {
                selectionGate.await()
            }
            if (item.lastSnapshot.status == EngineItemStatus.Failed) {
                throw IllegalStateException(
                    item.lastSnapshot.error?.toTestFailureMessage() ?: "Item failed to load",
                )
            }
            selectCallCount += 1
            snapshotState.value = snapshotState.value.copy(currentKitharaItemId = kitharaId)
            eventFlow.tryEmit(KitharaPlayerEvent.CurrentItemChanged(kitharaId))
        }
        every { wrapper.removeItem(any()) } answers {
            val kitharaId = firstArg<String>()
            val item = itemsById.remove(kitharaId) ?: return@answers
            itemsByUrl.remove(item.url)
        }
        every { wrapper.removeAllItems() } answers {
            removeAllItemsCallCount += 1
            itemsById.clear()
            itemsByUrl.clear()
            snapshotState.value = EnginePlayerSnapshot()
        }
    }

    fun item(url: String): TestKitharaItemHandle? = itemsByUrl[url]

    fun emitPlayerEvent(event: KitharaPlayerEvent) {
        eventFlow.tryEmit(event)
    }

    fun updatePosition(positionMs: Long, isPlaying: Boolean = snapshotState.value.rate > 0f) {
        snapshotState.value = snapshotState.value.copy(
            currentPositionMs = positionMs,
            rate = if (isPlaying) 1f else 0f,
            status = AudioEngineStatus.ReadyToPlay,
        )
    }

    fun blockSeek() {
        seekBlocked = true
        pendingSeekCallback = null
    }

    fun blockSelection() {
        selectionBlocked = true
        selectionGate = CompletableDeferred()
    }

    fun releaseBlockedSelection() {
        selectionBlocked = false
        selectionGate.complete(Unit)
    }

    fun finishBlockedSeek(success: Boolean, positionMs: Long) {
        seekBlocked = false
        if (success) {
            updatePosition(positionMs)
        }
        pendingSeekCallback?.invoke(success)
        pendingSeekCallback = null
    }

    fun resetRecordedOps() {
        insertedUrls.clear()
        removeAllItemsCallCount = 0
        selectCallCount = 0
    }

    private suspend fun suspendSeek(positionMs: Long): Boolean {
        val completion = CompletableDeferred<Boolean>()
        pendingSeekCallback = { success ->
            if (success) {
                updatePosition(positionMs)
            }
            completion.complete(success)
        }
        return completion.await()
    }
}

private class TestKitharaItemHandle(
    val url: String,
    override val kitharaId: String,
    private val emitEvent: (KitharaPlayerEvent) -> Boolean,
) : KitharaItemHandle {
    private val readyForSelection = CompletableDeferred<Unit>()
    var lastSnapshot: EngineItemSnapshot = EngineItemSnapshot()
        private set

    fun setSnapshot(snapshot: EngineItemSnapshot) {
        lastSnapshot = snapshot
        if (!readyForSelection.isCompleted &&
            (snapshot.status == EngineItemStatus.ReadyToPlay || snapshot.status == EngineItemStatus.Failed)
        ) {
            readyForSelection.complete(Unit)
        }
        snapshot.durationMs?.let { emitEvent(KitharaPlayerEvent.DurationDiscovered(kitharaId, it)) }
        when (snapshot.status) {
            EngineItemStatus.ReadyToPlay ->
                emitEvent(KitharaPlayerEvent.ItemReady(kitharaId, snapshot.durationMs))

            EngineItemStatus.Failed ->
                emitEvent(
                    KitharaPlayerEvent.ItemFailed(
                        kitharaId,
                        snapshot.error ?: AudioEngineError.LoadFailed("Unknown item error"),
                    ),
                )

            EngineItemStatus.Unknown -> Unit
        }
    }

    suspend fun awaitSelectionReady() {
        readyForSelection.await()
    }
}

private fun AudioEngineError.toTestFailureMessage(): String = when (this) {
    is AudioEngineError.LoadFailed -> message
    is AudioEngineError.StreamFailed -> message
    is AudioEngineError.EngineCrashed -> message
    AudioEngineError.SeekFailed -> "Seek failed"
}
