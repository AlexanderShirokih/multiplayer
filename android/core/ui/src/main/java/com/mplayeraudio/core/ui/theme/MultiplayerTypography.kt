package com.mplayeraudio.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Типографика приложения: размеры подобраны под макеты (docs/design/).
 * Для `MaterialTheme` значения преобразуются в [androidx.compose.material3.Typography] через [toMaterialTypography].
 */
data class MultiplayerTypography(
    val displayLarge: TextStyle,
    val headlineLarge: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val pageTitle: TextStyle,
    val pageSubtitle: TextStyle,
    val heroTitle: TextStyle,
    val cardTitleLarge: TextStyle,
    val cardTitleCompact: TextStyle,
    val cardMeta: TextStyle,
)

@Suppress("MagicNumber")
internal fun defaultMultiplayerTypography(): MultiplayerTypography {
    return MultiplayerTypography(
        displayLarge = TextStyle(
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        headlineLarge = TextStyle(
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        ),
        titleLarge = TextStyle(
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = TextStyle(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
        ),
        bodyLarge = TextStyle(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodyMedium = TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodySmall = TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
        ),
        labelLarge = TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        pageTitle = TextStyle(
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
        ),
        pageSubtitle = TextStyle(
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.1).sp,
        ),
        heroTitle = TextStyle(
            fontSize = 28.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
        ),
        cardTitleLarge = TextStyle(
            fontSize = 20.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.25).sp,
        ),
        cardTitleCompact = TextStyle(
            fontSize = 18.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        ),
        cardMeta = TextStyle(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.05).sp,
        ),
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
        bodySmall = bodySmall,
        labelLarge = labelLarge,
    )
}
