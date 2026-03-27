package com.multiplayer.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val BaselineTypography = Typography()

internal val MultiplayerTypography: Typography = Typography(
    displayLarge = BaselineTypography.displayLarge.copy(fontWeight = FontWeight.Bold),
    headlineLarge = BaselineTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaselineTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaselineTypography.titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = BaselineTypography.bodyLarge.copy(fontWeight = FontWeight.Normal),
    bodyMedium = BaselineTypography.bodyMedium.copy(fontWeight = FontWeight.Normal),
    labelLarge = BaselineTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)
