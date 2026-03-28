package com.mplayeraudio.core.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingStripViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `play pause dispatches command but does not optimistically change playback`() = runTest(dispatcher) {
        val controller = FakeNowPlayingStripController(
            initialState = NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                isPlaying = false,
            ),
        )
        val viewModel = NowPlayingStripViewModel(controller)
        advanceUntilIdle()

        viewModel.onAction(NowPlayingStripAction.PlayPauseClicked)
        advanceUntilIdle()

        assertEquals(1, controller.playCallCount)
        assertFalse(viewModel.state.value.isPlaying)
        viewModel.dispose()
    }

    @Test
    fun `previous and next do not mutate state locally`() = runTest(dispatcher) {
        val controller = FakeNowPlayingStripController(
            initialState = NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                isPlaying = true,
                currentPositionMs = 45_000L,
                durationMs = 180_000L,
            ),
        )
        val viewModel = NowPlayingStripViewModel(controller)
        advanceUntilIdle()
        val before = viewModel.state.value

        viewModel.onAction(NowPlayingStripAction.PreviousClicked)
        viewModel.onAction(NowPlayingStripAction.NextClicked)
        advanceUntilIdle()

        assertEquals(1, controller.previousCallCount)
        assertEquals(1, controller.nextCallCount)
        assertEquals(before, viewModel.state.value)
        viewModel.dispose()
    }

    @Test
    fun `seek drag updates only preview fraction`() = runTest(dispatcher) {
        val controller = FakeNowPlayingStripController(
            initialState = NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                currentPositionMs = 30_000L,
                durationMs = 120_000L,
            ),
        )
        val viewModel = NowPlayingStripViewModel(controller)
        advanceUntilIdle()

        viewModel.onAction(NowPlayingStripAction.SeekStarted)
        viewModel.onAction(NowPlayingStripAction.SeekChanged(0.8f))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSeekInProgress)
        assertEquals(0.25f, viewModel.state.value.progressFraction)
        assertEquals(0.8f, viewModel.state.value.displayedProgressFraction)
        viewModel.dispose()
    }

    @Test
    fun `seek finish calls controller and clears preview`() = runTest(dispatcher) {
        val controller = FakeNowPlayingStripController(
            initialState = NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                currentPositionMs = 30_000L,
                durationMs = 120_000L,
            ),
        )
        val viewModel = NowPlayingStripViewModel(controller)
        advanceUntilIdle()

        viewModel.onAction(NowPlayingStripAction.SeekStarted)
        viewModel.onAction(NowPlayingStripAction.SeekChanged(0.75f))
        viewModel.onAction(NowPlayingStripAction.SeekFinished(0.75f))
        advanceUntilIdle()

        assertEquals(90_000L, controller.lastSeekPositionMs)
        assertFalse(viewModel.state.value.isSeekInProgress)
        assertEquals(0.25f, viewModel.state.value.displayedProgressFraction)
        viewModel.dispose()
    }

    @Test
    fun `external state update replaces preview after seek finishes`() = runTest(dispatcher) {
        val controller = FakeNowPlayingStripController(
            initialState = NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                currentPositionMs = 30_000L,
                durationMs = 120_000L,
            ),
        )
        val viewModel = NowPlayingStripViewModel(controller)
        advanceUntilIdle()

        viewModel.onAction(NowPlayingStripAction.SeekStarted)
        viewModel.onAction(NowPlayingStripAction.SeekChanged(0.75f))
        advanceUntilIdle()
        controller.stateFlow.value = controller.stateFlow.value.copy(currentPositionMs = 105_000L)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSeekInProgress)
        assertEquals(0.75f, viewModel.state.value.displayedProgressFraction)

        viewModel.onAction(NowPlayingStripAction.SeekFinished(0.75f))
        advanceUntilIdle()
        controller.stateFlow.value = controller.stateFlow.value.copy(currentPositionMs = 105_000L)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSeekInProgress)
        assertEquals(0.875f, viewModel.state.value.displayedProgressFraction)
        viewModel.dispose()
    }
}

private class FakeNowPlayingStripController(
    initialState: NowPlayingStripExternalState,
) : NowPlayingStripController {
    val stateFlow = MutableStateFlow(initialState)

    var playCallCount = 0
    var pauseCallCount = 0
    var nextCallCount = 0
    var previousCallCount = 0
    var lastSeekPositionMs: Long? = null

    override val state = stateFlow

    override suspend fun play() {
        playCallCount += 1
    }

    override suspend fun pause() {
        pauseCallCount += 1
    }

    override suspend fun skipNext() {
        nextCallCount += 1
    }

    override suspend fun skipPrevious() {
        previousCallCount += 1
    }

    override suspend fun seekTo(positionMs: Long) {
        lastSeekPositionMs = positionMs
    }
}
