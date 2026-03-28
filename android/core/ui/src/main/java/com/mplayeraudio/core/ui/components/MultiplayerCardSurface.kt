@file:Suppress("MatchingDeclarationName")

package com.mplayeraudio.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

enum class MultiplayerCardSurfaceStyle {
    Surface2,
    Surface3,
    Surface4,
}

@Composable
fun MultiplayerCardSurface(
    modifier: Modifier = Modifier,
    style: MultiplayerCardSurfaceStyle = MultiplayerCardSurfaceStyle.Surface2,
    shape: Shape = RoundedCornerShape(MultiplayerTheme.radius.xLarge),
    contentPadding: PaddingValues = PaddingValues(MultiplayerTheme.spacing.md),
    border: BorderStroke = BorderStroke(
        width = 1.dp,
        color = MultiplayerTheme.colors.surfacePrimary.copy(alpha = 0.46f),
    ),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(multiplayerCardSurfaceBrush(style))
            .border(border = border, shape = shape)
            .then(clickableModifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
private fun multiplayerCardSurfaceBrush(
    style: MultiplayerCardSurfaceStyle,
): Brush {
    val colors = MultiplayerTheme.colors

    return when (style) {
        MultiplayerCardSurfaceStyle.Surface2 -> Brush.linearGradient(
            colors = listOf(
                colors.surface2GradientStart,
                colors.surface2GradientEnd,
            ),
        )

        MultiplayerCardSurfaceStyle.Surface3 -> Brush.linearGradient(
            colors = listOf(
                colors.surface3GradientStart,
                colors.surface3GradientEnd,
            ),
        )

        MultiplayerCardSurfaceStyle.Surface4 -> Brush.linearGradient(
            colors = listOf(
                colors.surface4GradientStart,
                colors.surface4GradientEnd,
            ),
        )
    }
}
