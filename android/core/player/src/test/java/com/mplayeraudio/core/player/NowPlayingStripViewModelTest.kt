package com.mplayeraudio.core.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
        val controllerState = MutableStateFlow(
            NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                isPlaying = false,
            ),
        )
        val controller = nowPlayingStripControllerMock(controllerState)
        val viewModel = NowPlayingStripViewModel(controller)
        advanceUntilIdle()

        viewModel.onAction(NowPlayingStripAction.PlayPauseClicked)
        advanceUntilIdle()

        coVerify(exactly = 1) { controller.play() }
        assertFalse(viewModel.state.value.isPlaying)
        viewModel.dispose()
    }

    @Test
    fun `previous and next do not mutate state locally`() = runTest(dispatcher) {
        val controllerState = MutableStateFlow(
            NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                isPlaying = true,
                currentPositionMs = 45_000L,
                durationMs = 180_000L,
            ),
        )
        val controller = nowPlayingStripControllerMock(controllerState)
        val viewModel = NowPlayingStripViewModel(controller)
        advanceUntilIdle()
        val before = viewModel.state.value

        viewModel.onAction(NowPlayingStripAction.PreviousClicked)
        viewModel.onAction(NowPlayingStripAction.NextClicked)
        advanceUntilIdle()

        coVerify(exactly = 1) { controller.skipPrevious() }
        coVerify(exactly = 1) { controller.skipNext() }
        assertEquals(before, viewModel.state.value)
        viewModel.dispose()
    }

    @Test
    fun `seek drag updates only preview fraction`() = runTest(dispatcher) {
        val controllerState = MutableStateFlow(
            NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                currentPositionMs = 30_000L,
                durationMs = 120_000L,
            ),
        )
        val controller = nowPlayingStripControllerMock(controllerState)
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
        val controllerState = MutableStateFlow(
            NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                currentPositionMs = 30_000L,
                durationMs = 120_000L,
            ),
        )
        val controller = nowPlayingStripControllerMock(controllerState)
        val viewModel = NowPlayingStripViewModel(controller)
        advanceUntilIdle()

        viewModel.onAction(NowPlayingStripAction.SeekStarted)
        viewModel.onAction(NowPlayingStripAction.SeekChanged(0.75f))
        viewModel.onAction(NowPlayingStripAction.SeekFinished(0.75f))
        advanceUntilIdle()

        coVerify(exactly = 1) { controller.seekTo(90_000L) }
        assertFalse(viewModel.state.value.isSeekInProgress)
        assertEquals(0.25f, viewModel.state.value.displayedProgressFraction)
        viewModel.dispose()
    }

    @Test
    fun `external state update replaces preview after seek finishes`() = runTest(dispatcher) {
        val controllerState = MutableStateFlow(
            NowPlayingStripExternalState(
                title = "Track",
                subtitle = "Album",
                currentPositionMs = 30_000L,
                durationMs = 120_000L,
            ),
        )
        val controller = nowPlayingStripControllerMock(controllerState)
        val viewModel = NowPlayingStripViewModel(controller)
        advanceUntilIdle()

        viewModel.onAction(NowPlayingStripAction.SeekStarted)
        viewModel.onAction(NowPlayingStripAction.SeekChanged(0.75f))
        advanceUntilIdle()
        controllerState.value = controllerState.value.copy(currentPositionMs = 105_000L)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSeekInProgress)
        assertEquals(0.75f, viewModel.state.value.displayedProgressFraction)

        viewModel.onAction(NowPlayingStripAction.SeekFinished(0.75f))
        advanceUntilIdle()
        controllerState.value = controllerState.value.copy(currentPositionMs = 105_000L)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSeekInProgress)
        assertEquals(0.875f, viewModel.state.value.displayedProgressFraction)
        viewModel.dispose()
    }
}

private fun nowPlayingStripControllerMock(
    state: MutableStateFlow<NowPlayingStripExternalState>,
): NowPlayingStripController {
    return mockk(relaxed = true) {
        every { this@mockk.state } returns state
        coEvery { play() } returns Unit
        coEvery { pause() } returns Unit
        coEvery { skipNext() } returns Unit
        coEvery { skipPrevious() } returns Unit
        coEvery { seekTo(any()) } returns Unit
    }
}
