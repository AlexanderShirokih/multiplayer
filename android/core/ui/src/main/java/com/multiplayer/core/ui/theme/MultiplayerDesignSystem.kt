package com.multiplayer.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.multiplayer.core.ui.tokens.MultiplayerElevation
import com.multiplayer.core.ui.tokens.MultiplayerIcons
import com.multiplayer.core.ui.tokens.MultiplayerRadius
import com.multiplayer.core.ui.tokens.MultiplayerSpacing
import com.multiplayer.core.ui.tokens.multiplayerColors

@Composable
fun MultiplayerDesignSystem(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme) { multiplayerColors(darkTheme) }
    val spacing = remember { MultiplayerSpacing() }
    val radius = remember { MultiplayerRadius() }
    val elevation = remember { MultiplayerElevation() }
    val icons = remember { MultiplayerIcons() }
    val materialColorScheme = remember(darkTheme) { multiplayerMaterialColorScheme(darkTheme) }
    val shapes = remember(radius) { multiplayerShapes(radius) }
    val typography = remember { defaultMultiplayerTypography() }

    CompositionLocalProvider(
        LocalMultiplayerColors provides colors,
        LocalMultiplayerSpacing provides spacing,
        LocalMultiplayerRadius provides radius,
        LocalMultiplayerElevation provides elevation,
        LocalMultiplayerIcons provides icons,
        LocalMultiplayerTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography.toMaterialTypography(),
            shapes = shapes,
            content = content,
        )
    }
}
