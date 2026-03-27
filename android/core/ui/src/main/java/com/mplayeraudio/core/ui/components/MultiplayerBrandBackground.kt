package com.mplayeraudio.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

@Composable
@Suppress("MagicNumber", "LongMethod")
fun MultiplayerBrandBackgroundDecor(
    modifier: Modifier = Modifier,
) {
    val colors = MultiplayerTheme.colors

    Box(modifier = modifier) {
        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-18).dp)
                .blur(
                    radius = 84.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 340.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualPrimary.copy(alpha = 0.98f),
                    colors.brandVisualPrimary.copy(alpha = 0.58f),
                    colors.brandVisualPrimaryContainer.copy(alpha = 0.22f),
                    Color.Transparent,
                ),
            ),
            alpha = 1f,
        )

        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 18.dp, y = 36.dp)
                .blur(
                    radius = 28.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 168.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualPrimaryMuted.copy(alpha = 0.9f),
                    colors.brandVisualPrimaryContainer.copy(alpha = 0.42f),
                    Color.Transparent,
                ),
            ),
            alpha = 0.96f,
        )

        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-40).dp, y = 158.dp)
                .blur(
                    radius = 58.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 232.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualSecondary.copy(alpha = 0.88f),
                    colors.brandVisualSecondary.copy(alpha = 0.34f),
                    Color.Transparent,
                ),
            ),
            alpha = 0.92f,
        )

        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = 190.dp)
                .blur(
                    radius = 18.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 96.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualSecondary.copy(alpha = 0.74f),
                    colors.brandVisualSecondary.copy(alpha = 0.2f),
                    Color.Transparent,
                ),
            ),
            alpha = 0.8f,
        )

        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 18.dp, y = 42.dp)
                .blur(
                    radius = 58.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 320.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualPrimaryContainer.copy(alpha = 0.34f),
                    Color.Transparent,
                ),
            ),
            alpha = 0.92f,
        )
    }
}

@Composable
private fun DecorativeGlow(
    size: Dp,
    brush: Brush,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .background(
                brush = brush,
                shape = CircleShape,
            ),
    )
}
