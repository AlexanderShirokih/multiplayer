package com.multiplayer.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

internal fun multiplayerMaterialColorScheme(
    darkTheme: Boolean,
): ColorScheme = if (darkTheme) {
    darkColorScheme(
        primary = Blue300,
        onPrimary = Ink950,
        primaryContainer = Blue500,
        onPrimaryContainer = Gray100,
        secondary = Gray300,
        onSecondary = Ink950,
        secondaryContainer = Gray700,
        onSecondaryContainer = Gray100,
        tertiary = Amber300,
        onTertiary = Ink950,
        background = Ink950,
        onBackground = Gray100,
        surface = Gray800,
        onSurface = Gray100,
        surfaceVariant = Gray700,
        onSurfaceVariant = Gray300,
        outline = Gray600,
        error = Red300,
        onError = Ink950,
    )
} else {
    lightColorScheme(
        primary = Blue500,
        onPrimary = Gray100,
        primaryContainer = Blue300,
        onPrimaryContainer = Ink950,
        secondary = Gray700,
        onSecondary = Gray100,
        secondaryContainer = Gray200,
        onSecondaryContainer = Ink950,
        tertiary = Amber300,
        onTertiary = Ink950,
        background = Gray100,
        onBackground = Ink950,
        surface = Gray100,
        onSurface = Ink950,
        surfaceVariant = Gray200,
        onSurfaceVariant = Gray700,
        outline = Gray500,
        error = Red300,
        onError = Gray100,
    )
}
