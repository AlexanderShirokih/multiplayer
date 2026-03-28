package com.mplayeraudio.core.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import com.mplayeraudio.core.ui.theme.MultiplayerDesignSystem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NowPlayingStripTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `central tap dispatches play pause`() {
        var lastAction: NowPlayingStripAction? = null

        composeRule.setContent {
            MultiplayerDesignSystem {
                NowPlayingStrip(
                    state = previewState(),
                    onAction = { action -> lastAction = action },
                )
            }
        }

        composeRule.onNodeWithTag("now_playing_play_pause").assertIsDisplayed()
        composeRule.onNodeWithTag("now_playing_strip").performTouchInput {
            click(center)
        }

        assertEquals(NowPlayingStripAction.PlayPauseClicked, lastAction)
    }

    @Test
    fun `prev and next dispatch correct actions`() {
        val actions = mutableListOf<NowPlayingStripAction>()

        composeRule.setContent {
            MultiplayerDesignSystem {
                NowPlayingStrip(
                    state = previewState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithTag("now_playing_previous").performClick()
        composeRule.onNodeWithTag("now_playing_next").performClick()

        assertEquals(
            listOf(
                NowPlayingStripAction.PreviousClicked,
                NowPlayingStripAction.NextClicked,
            ),
            actions,
        )
    }

    @Test
    fun `drag updates displayed progress and switches tail to straight mode`() {
        composeRule.setContent {
            MultiplayerDesignSystem {
                var state by mutableStateOf(previewState(isPlaying = true))
                NowPlayingStrip(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            NowPlayingStripAction.SeekStarted -> {
                                state = state.copy(isSeekInProgress = true)
                            }

                            is NowPlayingStripAction.SeekChanged -> {
                                state = state.copy(
                                    isSeekInProgress = true,
                                    displayedProgressFraction = action.fraction,
                                )
                            }

                            is NowPlayingStripAction.SeekFinished -> {
                                state = state.copy(
                                    isSeekInProgress = false,
                                    displayedProgressFraction = state.progressFraction,
                                )
                            }

                            else -> Unit
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("now_playing_strip")
            .assertIsDisplayed()
            .performTouchInput {
                val start = centerLeft.copy(x = centerLeft.x + 80f)
                val end = centerRight.copy(x = centerRight.x - 40f)
                down(start)
                moveTo(end)
            }

        composeRule.onNodeWithTag("now_playing_strip")
            .assert(hasStateDescription("straight-tail"))
            .assert(hasProgressAtLeast(0.6f))
    }

    private fun previewState(
        isPlaying: Boolean = false,
    ): NowPlayingStripState {
        val progress = 0.35f
        return NowPlayingStripState(
            title = "Midnight Echoes",
            subtitle = "Neon Avenue",
            isPlaying = isPlaying,
            currentPositionMs = 84_000L,
            durationMs = 240_000L,
            progressFraction = progress,
            displayedProgressFraction = progress,
            controlsEnabled = true,
        )
    }

    private fun hasStateDescription(value: String): SemanticsMatcher {
        return SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
            value,
        )
    }

    private fun hasProgressAtLeast(minValue: Float): SemanticsMatcher {
        return SemanticsMatcher("Progress at least $minValue") { node ->
            val key = androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo
            if (!node.config.contains(key)) {
                return@SemanticsMatcher false
            }
            val progress = node.config[key]
            progress.current >= minValue
        }
    }
}
