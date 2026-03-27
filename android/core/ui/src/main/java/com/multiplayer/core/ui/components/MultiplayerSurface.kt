package com.multiplayer.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.multiplayer.core.ui.theme.MultiplayerTheme

@Composable
fun MultiplayerSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MultiplayerTheme.colors.surfacePrimary,
    contentColor: Color = MultiplayerTheme.colors.textPrimary,
    tonalElevation: Dp = MultiplayerTheme.elevation.level0,
    shadowElevation: Dp = MultiplayerTheme.elevation.level0,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}
