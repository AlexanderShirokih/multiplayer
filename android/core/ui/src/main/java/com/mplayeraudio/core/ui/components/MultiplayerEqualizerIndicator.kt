package com.mplayeraudio.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mplayeraudio.core.ui.preview.MultiplayerPreview
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

private const val BarAnimation1Ms = 420
private const val BarAnimation2Ms = 560
private const val BarAnimation3Ms = 480

private const val BarPhaseOffset1Ms = 0
private const val BarPhaseOffset2Ms = 210
private const val BarPhaseOffset3Ms = 370

private const val Bar1InitialHeightFraction = 0.28f
private const val Bar2InitialHeightFraction = 0.45f
private const val Bar3InitialHeightFraction = 0.32f
private const val BarTargetHeightFraction = 1f

private val BarWidth: Dp = 6.dp
private val BarGap: Dp = 2.dp
private val BarMaxHeight: Dp = 26.dp
private val BarCornerRadius: Dp = 3.dp

@Composable
fun MultiplayerEqualizerIndicator(
    modifier: Modifier = Modifier,
    color: Color = MultiplayerTheme.colors.accent,
) {
    val transition = rememberInfiniteTransition(label = "multiplayer_equalizer")
    val bar1 by transition.animateFloat(
        initialValue = Bar1InitialHeightFraction,
        targetValue = BarTargetHeightFraction,
        animationSpec = infiniteRepeatable(
            animation = tween(BarAnimation1Ms, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(BarPhaseOffset1Ms),
        ),
        label = "eq_bar_1",
    )
    val bar2 by transition.animateFloat(
        initialValue = Bar2InitialHeightFraction,
        targetValue = BarTargetHeightFraction,
        animationSpec = infiniteRepeatable(
            animation = tween(BarAnimation2Ms, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(BarPhaseOffset2Ms),
        ),
        label = "eq_bar_2",
    )
    val bar3 by transition.animateFloat(
        initialValue = Bar3InitialHeightFraction,
        targetValue = BarTargetHeightFraction,
        animationSpec = infiniteRepeatable(
            animation = tween(BarAnimation3Ms, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(BarPhaseOffset3Ms),
        ),
        label = "eq_bar_3",
    )

    val bars = listOf(bar1, bar2, bar3)
    val shape = RoundedCornerShape(BarCornerRadius)

    Row(
        modifier = modifier.height(BarMaxHeight),
        horizontalArrangement = Arrangement.spacedBy(BarGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bars.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(BarWidth)
                    .height(BarMaxHeight * fraction)
                    .clip(shape)
                    .background(color),
            )
        }
    }
}

@Preview(showBackground = true, name = "Equalizer — light")
@Composable
private fun MultiplayerEqualizerIndicatorLightPreview() {
    MultiplayerPreview(contentAlignment = Alignment.Center) {
        MultiplayerEqualizerIndicator()
    }
}

@Preview(showBackground = true, name = "Equalizer — dark")
@Composable
private fun MultiplayerEqualizerIndicatorDarkPreview() {
    MultiplayerPreview(
        darkTheme = true,
        contentAlignment = Alignment.Center,
    ) {
        MultiplayerEqualizerIndicator()
    }
}
