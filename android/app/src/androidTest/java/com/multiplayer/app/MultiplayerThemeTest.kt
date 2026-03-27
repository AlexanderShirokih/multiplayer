package com.multiplayer.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.multiplayer.core.ui.components.MultiplayerSurface
import com.multiplayer.core.ui.components.MultiplayerText
import com.multiplayer.core.ui.theme.MultiplayerDesignSystem
import com.multiplayer.core.ui.theme.MultiplayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiplayerThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun multiplayerDesignSystem_providesExpectedTokens() {
        var spacingValue = 0.dp
        var radiusValue = 0.dp
        var elevationValue = 0.dp
        var textColorLabel = ""

        composeRule.setContent {
            MultiplayerDesignSystem {
                spacingValue = MultiplayerTheme.spacing.md
                radiusValue = MultiplayerTheme.radius.medium
                elevationValue = MultiplayerTheme.elevation.level2
                textColorLabel = MultiplayerTheme.colors.textPrimary.toString()
            }
        }

        composeRule.runOnIdle {
            assertEquals(16.dp, spacingValue)
            assertEquals(12.dp, radiusValue)
            assertEquals(6.dp, elevationValue)
            assertTrue(textColorLabel.isNotBlank())
        }
    }

    @Test
    fun wrappers_renderContentInsideTheme() {
        composeRule.setContent {
            MultiplayerDesignSystem {
                MultiplayerSurface {
                    MultiplayerText(text = "Design system ready")
                }
            }
        }

        composeRule.onNodeWithText("Design system ready").assertExists()
    }

    @Test
    fun themeAccessOutsideDesignSystem_throwsReadableError() {
        var thrown: Throwable? = null

        try {
            composeRule.setContent {
                MultiplayerTheme.colors
            }
            composeRule.waitForIdle()
        } catch (error: Throwable) {
            thrown = error
        }

        assertNotNull(thrown)
        assertTrue(
            collectErrorMessages(thrown).any { message ->
                message.contains("MultiplayerDesignSystem")
            },
        )
    }

    private fun collectErrorMessages(error: Throwable?): List<String> {
        val messages = mutableListOf<String>()
        var current = error

        while (current != null) {
            current.message?.let(messages::add)
            current = current.cause
        }

        return messages
    }
}
