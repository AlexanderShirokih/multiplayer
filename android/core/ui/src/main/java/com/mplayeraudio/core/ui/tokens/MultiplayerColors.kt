package com.mplayeraudio.core.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.mplayeraudio.core.ui.theme.AmberGradientLight
import com.mplayeraudio.core.ui.theme.Amber300
import com.mplayeraudio.core.ui.theme.BlueGray50
import com.mplayeraudio.core.ui.theme.Blue300
import com.mplayeraudio.core.ui.theme.Blue400
import com.mplayeraudio.core.ui.theme.Blue500
import com.mplayeraudio.core.ui.theme.Gray100
import com.mplayeraudio.core.ui.theme.Gray200
import com.mplayeraudio.core.ui.theme.Gray300
import com.mplayeraudio.core.ui.theme.Gray400
import com.mplayeraudio.core.ui.theme.Gray500
import com.mplayeraudio.core.ui.theme.Gray600
import com.mplayeraudio.core.ui.theme.Gray700
import com.mplayeraudio.core.ui.theme.Gray750
import com.mplayeraudio.core.ui.theme.Gray800
import com.mplayeraudio.core.ui.theme.Gray850
import com.mplayeraudio.core.ui.theme.Gray900
import com.mplayeraudio.core.ui.theme.Indigo700
import com.mplayeraudio.core.ui.theme.Indigo900
import com.mplayeraudio.core.ui.theme.Ink950
import com.mplayeraudio.core.ui.theme.Lavender50
import com.mplayeraudio.core.ui.theme.Lavender100
import com.mplayeraudio.core.ui.theme.Navy600
import com.mplayeraudio.core.ui.theme.NeutralBlue50
import com.mplayeraudio.core.ui.theme.Peach50
import com.mplayeraudio.core.ui.theme.Purple300
import com.mplayeraudio.core.ui.theme.Purple400
import com.mplayeraudio.core.ui.theme.Red300
import com.mplayeraudio.core.ui.theme.Slate600
import com.mplayeraudio.core.ui.theme.Slate900

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
    val surface2GradientStart: Color,
    val surface2GradientEnd: Color,
    val surface3GradientStart: Color,
    val surface3GradientEnd: Color,
    val surface4GradientStart: Color,
    val surface4GradientEnd: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textInverse: Color,
    val accent: Color,
    val accentMuted: Color,
    val brandVisualPrimary: Color,
    val brandVisualPrimaryMuted: Color,
    val brandVisualPrimaryContainer: Color,
    val brandVisualSecondary: Color,
    val brandVisualSecondaryMuted: Color,
    val brandVisualTertiary: Color,
    val brandVisualTertiaryMuted: Color,
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
    backgroundGradientEnd = BlueGray50,
    surfacePrimary = Color.White,
    surfaceSecondary = Gray200,
    surfaceOverlay = Gray200.copy(alpha = 0.92f),
    surfaceAccent = Blue300,
    surface2GradientStart = Color.White,
    surface2GradientEnd = BlueGray50,
    surface3GradientStart = Lavender50,
    surface3GradientEnd = Lavender100,
    surface4GradientStart = Peach50,
    surface4GradientEnd = NeutralBlue50,
    textPrimary = Ink950,
    textSecondary = Gray700,
    textTertiary = Gray500,
    textInverse = Color.White,
    accent = Blue500,
    accentMuted = Blue300,
    brandVisualPrimary = Blue500,
    brandVisualPrimaryMuted = Blue300,
    brandVisualPrimaryContainer = Blue400,
    brandVisualSecondary = Amber300,
    brandVisualSecondaryMuted = Amber300.copy(alpha = 0.48f),
    brandVisualTertiary = Purple400,
    brandVisualTertiaryMuted = Purple300,
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
    backgroundGradientEnd = Navy600,
    surfacePrimary = Gray900,
    surfaceSecondary = Gray850,
    surfaceOverlay = Gray900.copy(alpha = 0.88f),
    surfaceAccent = Blue500,
    surface2GradientStart = Gray900,
    surface2GradientEnd = Gray750,
    surface3GradientStart = Indigo900,
    surface3GradientEnd = Indigo700,
    surface4GradientStart = Slate900,
    surface4GradientEnd = Slate600,
    textPrimary = Gray100,
    textSecondary = Gray300,
    textTertiary = Gray400,
    textInverse = Ink950,
    accent = Blue300,
    accentMuted = Blue400,
    brandVisualPrimary = Blue400,
    brandVisualPrimaryMuted = Blue300,
    brandVisualPrimaryContainer = Blue500,
    brandVisualSecondary = Amber300,
    brandVisualSecondaryMuted = Amber300.copy(alpha = 0.48f),
    brandVisualTertiary = Purple400,
    brandVisualTertiaryMuted = Purple300,
    ctaGradientStart = AmberGradientLight,
    ctaGradientEnd = Amber300,
    borderSubtle = Gray600,
    borderStrong = Gray500,
    success = Blue300,
    warning = Amber300,
    error = Red300,
)
