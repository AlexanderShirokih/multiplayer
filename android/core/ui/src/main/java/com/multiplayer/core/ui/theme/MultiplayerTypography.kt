package com.multiplayer.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Типографика приложения: только те роли, которые используются в UI и настройках темы.
 * Для `MaterialTheme` значения преобразуются в [androidx.compose.material3.Typography] через [toMaterialTypography].
 */
data class MultiplayerTypography(
    val displayLarge: TextStyle,
    val headlineLarge: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val labelLarge: TextStyle,
)

internal fun defaultMultiplayerTypography(): MultiplayerTypography {
    val baseline = Typography()
    return MultiplayerTypography(
        displayLarge = baseline.displayLarge.copy(fontWeight = FontWeight.Bold),
        headlineLarge = baseline.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = baseline.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = baseline.titleMedium.copy(fontWeight = FontWeight.Medium),
        bodyLarge = baseline.bodyLarge.copy(fontWeight = FontWeight.Normal),
        bodyMedium = baseline.bodyMedium.copy(fontWeight = FontWeight.Normal),
        labelLarge = baseline.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

internal fun MultiplayerTypography.toMaterialTypography(): Typography {
    val baseline = Typography()
    return baseline.copy(
        displayLarge = displayLarge,
        headlineLarge = headlineLarge,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        labelLarge = labelLarge,
    )
}
