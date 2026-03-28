@file:Suppress("MagicNumber")

package com.mplayeraudio.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mplayeraudio.core.ui.R
import com.mplayeraudio.core.ui.preview.MultiplayerPreview
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

private const val RecordSizeFraction = 0.768f
private const val LightStartXRatio = 0.474f
private const val LightStartYRatio = 0.049f
private const val LightEndXRatio = 0.641f
private const val LightEndYRatio = 0.915f
private const val DarkStartXRatio = 0.139f
private const val DarkStartYRatio = 0.152f
private const val DarkEndXRatio = 0.927f
private const val DarkEndYRatio = 0.978f
private const val BorderStartXRatio = 0.162f
private const val BorderStartYRatio = 0.195f
private const val BorderEndXRatio = 0.85f
private const val BorderEndYRatio = 0.917f
private const val BorderStrokeRatio = 0.0039f

@Composable
fun MultiplayerVinylCover(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val cornerRadius = MultiplayerTheme.radius.large
    val isDarkTheme = MultiplayerTheme.colors.background.luminance() < 0.5f
    val shape = RoundedCornerShape(cornerRadius)
    val backgroundStart = if (isDarkTheme) {
        Color(0xFF0E1628)
    } else {
        Color(0xFFEFF5FF)
    }
    val backgroundEnd = if (isDarkTheme) {
        Color(0xFF17233B)
    } else {
        Color(0xFFC8D8FC)
    }
    val gradientStart = if (isDarkTheme) {
        Offset(DarkStartXRatio, DarkStartYRatio)
    } else {
        Offset(LightStartXRatio, LightStartYRatio)
    }
    val gradientEnd = if (isDarkTheme) {
        Offset(DarkEndXRatio, DarkEndYRatio)
    } else {
        Offset(LightEndXRatio, LightEndYRatio)
    }
    val borderStart = Color(0x42F9FCFF)
    val borderEnd = Color(0x0AD9E6FF)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .drawWithCache {
                val backgroundBrush = Brush.linearGradient(
                    colors = listOf(backgroundStart, backgroundEnd),
                    start = Offset(
                        x = size.width * gradientStart.x,
                        y = size.height * gradientStart.y,
                    ),
                    end = Offset(
                        x = size.width * gradientEnd.x,
                        y = size.height * gradientEnd.y,
                    ),
                )
                val borderBrush = Brush.linearGradient(
                    colors = listOf(borderStart, borderEnd),
                    start = Offset(
                        x = size.width * BorderStartXRatio,
                        y = size.height * BorderStartYRatio,
                    ),
                    end = Offset(
                        x = size.width * BorderEndXRatio,
                        y = size.height * BorderEndYRatio,
                    ),
                )
                val strokeWidth = size.minDimension * BorderStrokeRatio
                val drawCornerRadius = CornerRadius(
                    x = cornerRadius.toPx(),
                    y = cornerRadius.toPx(),
                )

                onDrawBehind {
                    drawRoundRect(
                        brush = backgroundBrush,
                        cornerRadius = drawCornerRadius,
                    )
                    drawRoundRect(
                        brush = borderBrush,
                        cornerRadius = drawCornerRadius,
                        style = Stroke(width = strokeWidth),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_multiplayer_vinyl_record),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(RecordSizeFraction),
        )
    }
}

@Preview(showBackground = true, name = "Vinyl Cover Light")
@Composable
private fun MultiplayerVinylCoverLightPreview() {
    MultiplayerPreview(contentAlignment = Alignment.Center) {
        MultiplayerVinylCover(modifier = Modifier.size(184.dp))
    }
}

@Preview(showBackground = true, name = "Vinyl Cover Dark")
@Composable
private fun MultiplayerVinylCoverDarkPreview() {
    MultiplayerPreview(
        darkTheme = true,
        contentAlignment = Alignment.Center,
    ) {
        MultiplayerVinylCover(modifier = Modifier.size(184.dp))
    }
}
