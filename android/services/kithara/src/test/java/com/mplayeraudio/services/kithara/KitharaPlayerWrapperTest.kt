package com.mplayeraudio.services.kithara

import com.kithara.ItemState
import com.kithara.ItemStatus
import com.kithara.KitharaError
import com.kithara.KitharaPlayerEvent as FfiKitharaPlayerEvent
import com.kithara.KitharaPlayerItem
import com.kithara.PlayerState
import com.kithara.PlayerStatus
import io.mockk.coEvery
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KitharaPlayerWrapperTest {

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `snapshots map player state and current item event`() = runTest {
        val playerState = MutableStateFlow(
            playerState(
                status = PlayerStatus.Unknown,
                currentTimeSeconds = 0.0,
                bufferedDurationSeconds = 0.0,
                rate = 0f,
                error = null,
            ),
        )
        val events = MutableSharedFlow<FfiKitharaPlayerEvent>(extraBufferCapacity = 1)
        val player = mockPlayer(playerState = playerState, events = events)
        val wrapperScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val wrapper = KitharaPlayerWrapper(scope = wrapperScope, player = player)
        val snapshotAwait = async {
            wrapper.snapshots.first { snapshot ->
                snapshot.status == AudioEngineStatus.ReadyToPlay &&
                    snapshot.currentKitharaItemId == "item-42"
            }
        }
        val collectJob = async {
            wrapper.events.take(1).toList(mutableListOf())
        }
        advanceUntilIdle()

        playerState.value = playerState(
            status = PlayerStatus.ReadyToPlay,
            currentTimeSeconds = 12.3,
            bufferedDurationSeconds = 20.0,
            rate = 1f,
            error = null,
        )
        events.tryEmit(FfiKitharaPlayerEvent.CurrentItemChanged("item-42"))
        val snapshot = snapshotAwait.await()

        assertEquals(AudioEngineStatus.ReadyToPlay, snapshot.status)
        assertEquals(12_300L, snapshot.currentPositionMs)
        assertEquals(20_000L, snapshot.bufferedPositionMs)
        assertEquals(1f, snapshot.rate)
        assertEquals("item-42", snapshot.currentKitharaItemId)
        collectJob.await()
        wrapperScope.cancel()
    }

    @Test
    fun `insertItem observes item state and emits duration and ready events`() = runTest {
        val itemState = MutableStateFlow(itemState(status = ItemStatus.Unknown, durationSeconds = null))
        val item = mockItem(id = "item-1", state = itemState)
        val player = mockPlayer()
        every { player.createItem("http://host/track.mp3") } returns item
        every { player.insert(item) } just Runs

        val wrapper = KitharaPlayerWrapper(scope = backgroundScope, player = player)
        val collectedEvents = mutableListOf<KitharaPlayerEvent>()
        val collectJob = launch {
            wrapper.events.take(2).toList(collectedEvents)
        }
        advanceUntilIdle()

        val handle = wrapper.insertItem(url = "http://host/track.mp3")
        itemState.value = itemState(status = ItemStatus.ReadyToPlay, durationSeconds = 42.0)
        advanceUntilIdle()
        collectJob.join()

        assertEquals("item-1", handle.kitharaId)
        assertEquals(
            listOf(
                KitharaPlayerEvent.DurationDiscovered("item-1", 42_000L),
                KitharaPlayerEvent.ItemReady("item-1", 42_000L),
            ),
            collectedEvents,
        )
        wrapper.removeAllItems()
        verify(exactly = 1) { player.createItem("http://host/track.mp3") }
        verify(exactly = 1) { player.insert(item) }
    }

    @Test
    fun `selectItem waits for readiness and selects inserted queue item`() = runTest {
        val itemState = MutableStateFlow(itemState(status = ItemStatus.Unknown, durationSeconds = null))
        val item = mockItem(id = "item-7", state = itemState)
        val player = mockPlayer()
        every { player.createItem("http://host/track.mp3") } returns item
        every { player.insert(item) } just Runs
        every { player.selectItem(index = 0, autoplay = false) } just Runs

        val wrapper = KitharaPlayerWrapper(scope = backgroundScope, player = player)
        wrapper.insertItem(url = "http://host/track.mp3")

        val selection = async { wrapper.selectItem(kitharaId = "item-7") }
        assertFalse(selection.isCompleted)

        itemState.value = itemState(status = ItemStatus.ReadyToPlay, durationSeconds = 5.0)
        advanceUntilIdle()
        selection.await()

        wrapper.removeAllItems()
        verify(exactly = 1) { player.selectItem(index = 0, autoplay = false) }
    }

    @Test
    fun `selectItem retries when player is not ready`() = runTest {
        val itemState = MutableStateFlow(itemState(status = ItemStatus.ReadyToPlay, durationSeconds = 5.0))
        val item = mockItem(id = "item-9", state = itemState)
        val player = mockPlayer()
        every { player.createItem("http://host/track.mp3") } returns item
        every { player.insert(item) } just Runs

        var attempts = 0
        every { player.selectItem(index = 0, autoplay = false) } answers {
            attempts += 1
            if (attempts < 3) {
                throw KitharaError.NotReady
            }
        }

        val wrapper = KitharaPlayerWrapper(scope = backgroundScope, player = player)
        wrapper.insertItem(url = "http://host/track.mp3")

        wrapper.selectItem(kitharaId = "item-9")

        assertEquals(3, attempts)
        wrapper.removeAllItems()
    }

    @Test
    fun `seek returns callback result`() = runTest {
        val player = mockPlayer()
        coEvery { player.seek(15.0) } returns true

        val wrapper = KitharaPlayerWrapper(scope = backgroundScope, player = player)

        assertTrue(wrapper.seek(seconds = 15.0))
    }

    @Test
    fun `removeItem forgets inserted item and ignores missing id`() = runTest {
        val itemState = MutableStateFlow(itemState(status = ItemStatus.Unknown, durationSeconds = null))
        val item = mockItem(id = "item-remove", state = itemState)
        val player = mockPlayer()
        every { player.createItem("http://host/track.mp3") } returns item
        every { player.insert(item) } just Runs
        every { player.remove(item) } just Runs

        val wrapper = KitharaPlayerWrapper(scope = backgroundScope, player = player)
        wrapper.insertItem(url = "http://host/track.mp3")

        wrapper.removeItem(kitharaId = "item-remove")
        wrapper.removeItem(kitharaId = "missing")

        wrapper.removeAllItems()
        verify(exactly = 1) { player.remove(item) }
    }

    private fun mockPlayer(
        playerState: MutableStateFlow<PlayerState> = MutableStateFlow(
            playerState(
                status = PlayerStatus.Unknown,
                currentTimeSeconds = 0.0,
                bufferedDurationSeconds = 0.0,
                rate = 0f,
                error = null,
            ),
        ),
        events: MutableSharedFlow<FfiKitharaPlayerEvent> = MutableSharedFlow(extraBufferCapacity = 1),
    ): KitharaPlayerDriver {
        val player = mockk<KitharaPlayerDriver>(relaxed = true)
        every { player.state } returns playerState
        every { player.events } returns events
        every { player.play() } just Runs
        every { player.pause() } just Runs
        every { player.createItem(any()) } answers {
            KitharaPlayerItem(url = firstArg())
        }
        every { player.removeAllItems() } just Runs
        return player
    }

    private fun playerState(
        status: PlayerStatus,
        currentTimeSeconds: Double,
        bufferedDurationSeconds: Double,
        rate: Float,
        error: KitharaError?,
    ): PlayerState = PlayerState(
        bufferedDurationSeconds,
        currentTimeSeconds,
        null,
        error,
        emptyList(),
        rate,
        status,
    )

    private fun itemState(
        status: ItemStatus,
        durationSeconds: Double?,
        error: KitharaError? = null,
    ): ItemState = ItemState(
        0.0,
        durationSeconds,
        error,
        status,
    )

    private fun mockItem(
        id: String,
        state: MutableStateFlow<ItemState>,
    ): KitharaPlayerItem = mockk {
        every { this@mockk.id } returns id
        every { this@mockk.state } returns state
    }
}
