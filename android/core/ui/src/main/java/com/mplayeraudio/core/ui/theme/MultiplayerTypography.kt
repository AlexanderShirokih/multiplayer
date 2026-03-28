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
    val pageTitle: TextStyle,
    val title: TextStyle,
    val compactTitle: TextStyle,
    val body: TextStyle,
    val secondaryBody: TextStyle,
    val meta: TextStyle,
    val label: TextStyle,
)

@Suppress("MagicNumber")
internal fun defaultMultiplayerTypography(): MultiplayerTypography {
    return MultiplayerTypography(
        pageTitle = TextStyle(
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
        ),
        title = TextStyle(
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.25).sp,
        ),
        compactTitle = TextStyle(
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
        ),
        body = TextStyle(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
        ),
        secondaryBody = TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
        ),
        meta = TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
        ),
        label = TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

internal fun MultiplayerTypography.toMaterialTypography(): Typography {
    val baseline = Typography()
    return baseline.copy(
        displayLarge = pageTitle,
        headlineLarge = title,
        titleLarge = title,
        titleMedium = label,
        bodyLarge = body,
        bodyMedium = secondaryBody,
        bodySmall = meta,
        labelLarge = label,
    )
}
