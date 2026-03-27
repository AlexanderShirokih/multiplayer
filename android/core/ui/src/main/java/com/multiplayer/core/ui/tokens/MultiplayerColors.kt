package com.multiplayer.core.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.multiplayer.core.ui.theme.AmberGradientLight
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
    val backgroundGradientStart: Color,
    val backgroundGradientEnd: Color,
    val surfacePrimary: Color,
    val surfaceSecondary: Color,
    val surfaceOverlay: Color,
    val surfaceAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textInverse: Color,
    val accent: Color,
    val accentMuted: Color,
    val brandVisualPrimary: Color,
    val brandVisualPrimaryMuted: Color,
    val brandVisualPrimaryContainer: Color,
    val brandVisualSecondary: Color,
    val brandVisualSecondaryMuted: Color,
    val ctaGradientStart: Color,
    val ctaGradientEnd: Color,
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
    backgroundGradientStart = Gray100,
    backgroundGradientEnd = Gray200,
    surfacePrimary = Color.White,
    surfaceSecondary = Gray200,
    surfaceOverlay = Gray200.copy(alpha = 0.92f),
    surfaceAccent = Blue300,
    textPrimary = Ink950,
    textSecondary = Gray700,
    textInverse = Color.White,
    accent = Blue500,
    accentMuted = Blue300,
    brandVisualPrimary = Blue500,
    brandVisualPrimaryMuted = Blue300,
    brandVisualPrimaryContainer = Blue400,
    brandVisualSecondary = Amber300,
    brandVisualSecondaryMuted = Amber300.copy(alpha = 0.48f),
    ctaGradientStart = Amber300,
    ctaGradientEnd = AmberGradientLight,
    borderSubtle = Gray300,
    borderStrong = Gray500,
    success = Blue400,
    warning = Amber300,
    error = Red300,
)

private fun darkMultiplayerColors(): MultiplayerColors = MultiplayerColors(
    background = Ink950,
    backgroundMuted = Gray900,
    backgroundGradientStart = Ink950,
    backgroundGradientEnd = Gray900,
    surfacePrimary = Gray800,
    surfaceSecondary = Gray700,
    surfaceOverlay = Gray800.copy(alpha = 0.88f),
    surfaceAccent = Blue500,
    textPrimary = Gray100,
    textSecondary = Gray300,
    textInverse = Ink950,
    accent = Blue300,
    accentMuted = Blue400,
    brandVisualPrimary = Blue400,
    brandVisualPrimaryMuted = Blue300,
    brandVisualPrimaryContainer = Blue500,
    brandVisualSecondary = Amber300,
    brandVisualSecondaryMuted = Amber300.copy(alpha = 0.48f),
    ctaGradientStart = AmberGradientLight,
    ctaGradientEnd = Amber300,
    borderSubtle = Gray600,
    borderStrong = Gray500,
    success = Blue300,
    warning = Amber300,
    error = Red300,
)
