@file:Suppress("MagicNumber")

package com.mplayeraudio.core.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Immutable
data class MultiplayerIcons(
    val appLogo: ImageVector? = null,
    val play: ImageVector,
    val pause: ImageVector,
    val next: ImageVector,
    val previous: ImageVector,
)

internal fun multiplayerIcons(): MultiplayerIcons {
    return MultiplayerIcons(
        play = playIcon(),
        pause = pauseIcon(),
        next = nextIcon(),
        previous = previousIcon(),
    )
}

private fun playIcon(): ImageVector {
    return Builder(
        name = "MultiplayerPlay",
        defaultWidth = 16.dp,
        defaultHeight = 20.dp,
        viewportWidth = 16f,
        viewportHeight = 20f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(1.0f, 2.219f)
            curveTo(1.0f, 0.628f, 2.767f, -0.326f, 4.097f, 0.546f)
            lineTo(15.45f, 7.992f)
            curveTo(16.654f, 8.782f, 16.654f, 10.548f, 15.45f, 11.338f)
            lineTo(4.097f, 18.783f)
            curveTo(2.767f, 19.656f, 1.0f, 18.702f, 1.0f, 17.111f)
            close()
        }
    }.build()
}

private fun pauseIcon(): ImageVector {
    return Builder(
        name = "MultiplayerPause",
        defaultWidth = 22.dp,
        defaultHeight = 22.dp,
        viewportWidth = 22f,
        viewportHeight = 22f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(5f, 3f)
            lineTo(9f, 3f)
            curveTo(10.105f, 3f, 11f, 3.895f, 11f, 5f)
            lineTo(11f, 17f)
            curveTo(11f, 18.105f, 10.105f, 19f, 9f, 19f)
            lineTo(5f, 19f)
            curveTo(3.895f, 19f, 3f, 18.105f, 3f, 17f)
            lineTo(3f, 5f)
            curveTo(3f, 3.895f, 3.895f, 3f, 5f, 3f)
            close()
            moveTo(13f, 5f)
            curveTo(13f, 3.895f, 13.895f, 3f, 15f, 3f)
            lineTo(19f, 3f)
            curveTo(20.105f, 3f, 21f, 3.895f, 21f, 5f)
            lineTo(21f, 17f)
            curveTo(21f, 18.105f, 20.105f, 19f, 19f, 19f)
            lineTo(15f, 19f)
            curveTo(13.895f, 19f, 13f, 18.105f, 13f, 17f)
            close()
        }
    }.build()
}

private fun nextIcon(): ImageVector {
    return Builder(
        name = "MultiplayerNext",
        defaultWidth = 20.dp,
        defaultHeight = 24.dp,
        viewportWidth = 20f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(3f, 2.5f)
            lineTo(16.088f, 12.503f)
            lineTo(3f, 22.506f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(18f, 3f)
            lineTo(18f, 22f)
        }
    }.build()
}

private fun previousIcon(): ImageVector {
    return Builder(
        name = "MultiplayerPrevious",
        defaultWidth = 20.dp,
        defaultHeight = 24.dp,
        viewportWidth = 20f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(17f, 2.5f)
            lineTo(3.912f, 12.503f)
            lineTo(17f, 22.506f)
            close()
        }
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2f, 3f)
            lineTo(2f, 22f)
        }
    }.build()
}
