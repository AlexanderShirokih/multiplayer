package com.multiplayer.core.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.multiplayer.core.ui.theme.Amber300
import com.multiplayer.core.ui.theme.Blue300
import com.multiplayer.core.ui.theme.Blue400
import com.multiplayer.core.ui.theme.Blue500
import com.multiplayer.core.ui.theme.Gray100
import com.multiplayer.core.ui.theme.Gray200
import com.multiplayer.core.ui.theme.Gray300
import com.multiplayer.core.ui.theme.Gray500
import com.multiplayer.core.ui.theme.Gray600
import com.multiplayer.core.ui.theme.Gray700
import com.multiplayer.core.ui.theme.Gray800
import com.multiplayer.core.ui.theme.Gray900
import com.multiplayer.core.ui.theme.Ink950
import com.multiplayer.core.ui.theme.Red300

@Immutable
data class MultiplayerColors(
    val background: Color,
    val backgroundMuted: Color,
    val surfacePrimary: Color,
    val surfaceSecondary: Color,
    val surfaceAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textInverse: Color,
    val accent: Color,
    val accentMuted: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

internal fun multiplayerColors(
    darkTheme: Boolean,
): MultiplayerColors = if (darkTheme) {
    darkMultiplayerColors()
} else {
    lightMultiplayerColors()
}

private fun lightMultiplayerColors(): MultiplayerColors = MultiplayerColors(
    background = Gray100,
    backgroundMuted = Gray200,
    surfacePrimary = Color.White,
    surfaceSecondary = Gray200,
    surfaceAccent = Blue300,
    textPrimary = Ink950,
    textSecondary = Gray700,
    textInverse = Color.White,
    accent = Blue500,
    accentMuted = Blue300,
    borderSubtle = Gray300,
    borderStrong = Gray500,
    success = Blue400,
    warning = Amber300,
    error = Red300,
)

private fun darkMultiplayerColors(): MultiplayerColors = MultiplayerColors(
    background = Ink950,
    backgroundMuted = Gray900,
    surfacePrimary = Gray800,
    surfaceSecondary = Gray700,
    surfaceAccent = Blue500,
    textPrimary = Gray100,
    textSecondary = Gray300,
    textInverse = Ink950,
    accent = Blue300,
    accentMuted = Blue400,
    borderSubtle = Gray600,
    borderStrong = Gray500,
    success = Blue300,
    warning = Amber300,
    error = Red300,
)
