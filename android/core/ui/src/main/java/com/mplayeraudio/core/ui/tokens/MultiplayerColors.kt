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
import com.mplayeraudio.core.ui.theme.Gray650
import com.mplayeraudio.core.ui.theme.Gray750
import com.mplayeraudio.core.ui.theme.Gray900
import com.mplayeraudio.core.ui.theme.Indigo700
import com.mplayeraudio.core.ui.theme.Indigo900
import com.mplayeraudio.core.ui.theme.Ink950
import com.mplayeraudio.core.ui.theme.Lavender50
import com.mplayeraudio.core.ui.theme.Lavender100
import com.mplayeraudio.core.ui.theme.MiniPlayerDarkEnd
import com.mplayeraudio.core.ui.theme.MiniPlayerDarkSecondary
import com.mplayeraudio.core.ui.theme.MiniPlayerDarkStart
import com.mplayeraudio.core.ui.theme.MiniPlayerLightEnd
import com.mplayeraudio.core.ui.theme.MiniPlayerLightSecondary
import com.mplayeraudio.core.ui.theme.MiniPlayerLightStart
import com.mplayeraudio.core.ui.theme.Navy600
import com.mplayeraudio.core.ui.theme.NeutralBlue50
import com.mplayeraudio.core.ui.theme.Peach50
import com.mplayeraudio.core.ui.theme.Purple400
import com.mplayeraudio.core.ui.theme.Slate600
import com.mplayeraudio.core.ui.theme.Slate900

@Immutable
data class MultiplayerColorRamp(
    val start: Color,
    val end: Color,
)

@Immutable
data class MultiplayerColors(
    val background: Color,
    val backgroundGradient: MultiplayerColorRamp,
    val surfacePrimary: Color,
    val surfaceOverlay: Color,
    val surface2Gradient: MultiplayerColorRamp,
    val surface3Gradient: MultiplayerColorRamp,
    val surface4Gradient: MultiplayerColorRamp,
    val textPrimary: Color,
    val textSecondary: Color,
    val surfaceContentPrimary: Color,
    val surfaceContentSecondary: Color,
    val textInverse: Color,
    val accent: Color,
    val brandVisualPrimary: Color,
    val brandVisualPrimaryMuted: Color,
    val brandVisualPrimaryContainer: Color,
    val brandVisualSecondary: Color,
    val brandVisualTertiary: Color,
    val ctaGradient: MultiplayerColorRamp,
    val miniPlayerGradient: MultiplayerColorRamp,
    val miniPlayerProgress: Color,
    val miniPlayerPrimaryContent: Color,
    val miniPlayerSecondaryContent: Color,
    val borderSubtle: Color,
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
    backgroundGradient = MultiplayerColorRamp(
        start = Gray100,
        end = BlueGray50,
    ),
    surfacePrimary = Color.White,
    surfaceOverlay = Gray200.copy(alpha = 0.92f),
    surface2Gradient = MultiplayerColorRamp(
        start = Color.White,
        end = BlueGray50,
    ),
    surface3Gradient = MultiplayerColorRamp(
        start = Lavender50,
        end = Lavender100,
    ),
    surface4Gradient = MultiplayerColorRamp(
        start = Peach50,
        end = NeutralBlue50,
    ),
    textPrimary = Ink950,
    textSecondary = Gray700,
    surfaceContentPrimary = Gray650,
    surfaceContentSecondary = Gray500,
    textInverse = Color.White,
    accent = Blue500,
    brandVisualPrimary = Blue500,
    brandVisualPrimaryMuted = Blue300,
    brandVisualPrimaryContainer = Blue400,
    brandVisualSecondary = Amber300,
    brandVisualTertiary = Purple400,
    ctaGradient = MultiplayerColorRamp(
        start = Amber300,
        end = AmberGradientLight,
    ),
    miniPlayerGradient = MultiplayerColorRamp(
        start = MiniPlayerLightStart,
        end = MiniPlayerLightEnd,
    ),
    miniPlayerProgress = Amber300,
    miniPlayerPrimaryContent = Gray650,
    miniPlayerSecondaryContent = MiniPlayerLightSecondary,
    borderSubtle = Gray300,
)

private fun darkMultiplayerColors(): MultiplayerColors = MultiplayerColors(
    background = Ink950,
    backgroundGradient = MultiplayerColorRamp(
        start = Ink950,
        end = Navy600,
    ),
    surfacePrimary = Gray900,
    surfaceOverlay = Gray900.copy(alpha = 0.88f),
    surface2Gradient = MultiplayerColorRamp(
        start = Gray900,
        end = Gray750,
    ),
    surface3Gradient = MultiplayerColorRamp(
        start = Indigo900,
        end = Indigo700,
    ),
    surface4Gradient = MultiplayerColorRamp(
        start = Slate900,
        end = Slate600,
    ),
    textPrimary = Gray100,
    textSecondary = Gray300,
    surfaceContentPrimary = Gray100,
    surfaceContentSecondary = Gray400,
    textInverse = Ink950,
    accent = Blue300,
    brandVisualPrimary = Blue400,
    brandVisualPrimaryMuted = Blue300,
    brandVisualPrimaryContainer = Blue500,
    brandVisualSecondary = Amber300,
    brandVisualTertiary = Purple400,
    ctaGradient = MultiplayerColorRamp(
        start = AmberGradientLight,
        end = Amber300,
    ),
    miniPlayerGradient = MultiplayerColorRamp(
        start = MiniPlayerDarkStart,
        end = MiniPlayerDarkEnd,
    ),
    miniPlayerProgress = Amber300,
    miniPlayerPrimaryContent = Gray100,
    miniPlayerSecondaryContent = MiniPlayerDarkSecondary,
    borderSubtle = Gray600,
)
