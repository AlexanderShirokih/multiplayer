package com.multiplayer.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.multiplayer.core.ui.tokens.MultiplayerColors
import com.multiplayer.core.ui.tokens.MultiplayerElevation
import com.multiplayer.core.ui.tokens.MultiplayerIcons
import com.multiplayer.core.ui.tokens.MultiplayerRadius
import com.multiplayer.core.ui.tokens.MultiplayerSpacing

internal val LocalMultiplayerColors = staticCompositionLocalOf<MultiplayerColors> {
    error("MultiplayerTheme.colors is not available. Wrap your UI in MultiplayerDesignSystem.")
}

internal val LocalMultiplayerSpacing = staticCompositionLocalOf<MultiplayerSpacing> {
    error("MultiplayerTheme.spacing is not available. Wrap your UI in MultiplayerDesignSystem.")
}

internal val LocalMultiplayerRadius = staticCompositionLocalOf<MultiplayerRadius> {
    error("MultiplayerTheme.radius is not available. Wrap your UI in MultiplayerDesignSystem.")
}

internal val LocalMultiplayerElevation = staticCompositionLocalOf<MultiplayerElevation> {
    error("MultiplayerTheme.elevation is not available. Wrap your UI in MultiplayerDesignSystem.")
}

internal val LocalMultiplayerIcons = staticCompositionLocalOf<MultiplayerIcons> {
    error("MultiplayerTheme.icons is not available. Wrap your UI in MultiplayerDesignSystem.")
}

object MultiplayerTheme {
    val colors: MultiplayerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMultiplayerColors.current

    val spacing: MultiplayerSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalMultiplayerSpacing.current

    val radius: MultiplayerRadius
        @Composable
        @ReadOnlyComposable
        get() = LocalMultiplayerRadius.current

    val elevation: MultiplayerElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalMultiplayerElevation.current

    val icons: MultiplayerIcons
        @Composable
        @ReadOnlyComposable
        get() = LocalMultiplayerIcons.current

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val materialColorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme
}
