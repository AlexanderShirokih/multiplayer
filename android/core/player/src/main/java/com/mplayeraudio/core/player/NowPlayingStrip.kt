@file:Suppress("MagicNumber", "LongMethod")

package com.mplayeraudio.core.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mplayeraudio.core.ui.components.MultiplayerText
import com.mplayeraudio.core.ui.preview.MultiplayerPreview
import com.mplayeraudio.core.ui.theme.MultiplayerTheme
import kotlin.math.PI
import kotlin.math.sin

private val StripHeight = 123.468.dp
private val StripCornerRadius = 20.dp
private val StripBorderWidth = 1.dp
private val SideControlHitWidth = 48.dp
private val PlayPauseIconSize = 18.dp
private val SideIconSize = 20.dp
private val ProgressWaveWidth = 22.dp
private val ProgressWaveAmplitude = 8.dp
private const val ProgressWavePrimaryCycles = 2.4f
private const val ProgressWaveHarmonicCycles = 4.8f
private const val ProgressWaveHarmonicStrength = 0.32f

@Composable
fun NowPlayingStrip(
    viewModel: NowPlayingStripViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NowPlayingStrip(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
@Suppress("CyclomaticComplexMethod")
fun NowPlayingStrip(
    state: NowPlayingStripState,
    onAction: (NowPlayingStripAction) -> Unit,
    modifier: Modifier = Modifier,
    showBorder: Boolean = true,
) {
    val colors = MultiplayerTheme.colors
    val spacing = MultiplayerTheme.spacing
    val typography = MultiplayerTheme.typography
    val icons = MultiplayerTheme.icons
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(StripCornerRadius)
    val infiniteTransition = rememberInfiniteTransition(label = "mini-player-wave")
    val animatedWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mini-player-wave-phase",
    )
    val wavePhase = if (state.isPlaying && !state.isSeekInProgress) animatedWavePhase else 0f
    val trailingGestureExclusionPx = with(density) { SideControlHitWidth.toPx() }
    var stripWidthPx by remember { mutableIntStateOf(0) }
    var lastSeekFraction by remember(state.displayedProgressFraction) {
        mutableFloatStateOf(state.displayedProgressFraction)
    }

    Box(
        modifier = modifier
            .testTag("now_playing_strip")
            .fillMaxWidth()
            .height(StripHeight)
            .onSizeChanged { size ->
                stripWidthPx = size.width
            }
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colors.miniPlayerGradient.start,
                        colors.miniPlayerGradient.end,
                    ),
                ),
            )
            .let { baseModifier ->
                if (showBorder) {
                    baseModifier.border(
                        width = StripBorderWidth,
                        color = colors.surfacePrimary.copy(alpha = 0.46f),
                        shape = shape,
                    )
                } else {
                    baseModifier
                }
            }
            .drawWithCache {
                val radiusPx = StripCornerRadius.toPx()
                val clipPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(Offset.Zero, size),
                            radiusX = radiusPx,
                            radiusY = radiusPx,
                        ),
                    )
                }
                onDrawWithContent {
                    clipPath(clipPath) {
                        drawMiniPlayerProgress(
                            progress = state.displayedProgressFraction,
                            color = colors.miniPlayerProgress.copy(alpha = 0.7f),
                            isWaveAnimated = state.isPlaying && !state.isSeekInProgress,
                            wavePhase = wavePhase,
                            waveWidth = ProgressWaveWidth.toPx(),
                            amplitude = ProgressWaveAmplitude.toPx(),
                        )
                    }
                    drawContent()
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = state.displayedProgressFraction,
                    range = 0f..1f,
                    steps = 0,
                )
                stateDescription = if (state.isPlaying && !state.isSeekInProgress) {
                    "wave-tail"
                } else {
                    "straight-tail"
                }
            }
            .pointerInput(state.controlsEnabled, stripWidthPx) {
                var dragActive = false
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = stripWidthPx.toFloat()
                        val insideSeekArea = offset.x > trailingGestureExclusionPx &&
                            offset.x < width - trailingGestureExclusionPx
                        if (!insideSeekArea || !state.controlsEnabled || width <= 0f) {
                            dragActive = false
                            lastSeekFraction = state.displayedProgressFraction
                        } else {
                            dragActive = true
                            val fraction = (offset.x / width).coerceIn(0f, 1f)
                            lastSeekFraction = fraction
                            onAction(NowPlayingStripAction.SeekStarted)
                            onAction(NowPlayingStripAction.SeekChanged(fraction))
                        }
                    },
                    onDragCancel = {
                        if (dragActive) {
                            dragActive = false
                            onAction(NowPlayingStripAction.SeekFinished(lastSeekFraction))
                        }
                    },
                    onDragEnd = {
                        if (dragActive) {
                            dragActive = false
                            onAction(NowPlayingStripAction.SeekFinished(lastSeekFraction))
                        }
                    },
                ) { change, _ ->
                    val width = stripWidthPx.toFloat()
                    if (!dragActive || !state.controlsEnabled || width <= 0f) {
                        return@detectDragGestures
                    }

                    val fraction = (change.position.x / width).coerceIn(0f, 1f)
                    lastSeekFraction = fraction
                    onAction(NowPlayingStripAction.SeekChanged(fraction))
                    change.consume()
                }
            }
            .padding(horizontal = spacing.sm),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ControlButton(
                tag = "now_playing_previous",
                icon = icons.previous,
                contentDescription = "Предыдущий трек",
                enabled = state.controlsEnabled,
                iconSize = SideIconSize,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAction(NowPlayingStripAction.PreviousClicked)
                },
            )

            Row(
                modifier = Modifier
                    .padding(horizontal = spacing.xxs)
                    .clickable(enabled = state.controlsEnabled) {
                        onAction(NowPlayingStripAction.PlayPauseClicked)
                    },
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) icons.pause else icons.play,
                    contentDescription = if (state.isPlaying) "Пауза" else "Воспроизвести",
                    modifier = Modifier
                        .testTag("now_playing_play_pause")
                        .size(PlayPauseIconSize),
                    tint = colors.miniPlayerPrimaryContent,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    MultiplayerText(
                        text = state.title,
                        style = typography.title,
                        color = colors.miniPlayerPrimaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.subtitle.isNotBlank()) {
                        MultiplayerText(
                            text = state.subtitle,
                            style = typography.secondaryBody,
                            color = colors.miniPlayerSecondaryContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            ControlButton(
                tag = "now_playing_next",
                icon = icons.next,
                contentDescription = "Следующий трек",
                enabled = state.controlsEnabled,
                iconSize = SideIconSize,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAction(NowPlayingStripAction.NextClicked)
                },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.Transparent),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = spacing.xs,
                    end = spacing.xs,
                    bottom = spacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MultiplayerText(
                text = formatPlaybackTime(state.currentPositionMs),
                style = typography.meta,
                color = colors.miniPlayerSecondaryContent,
            )
            Spacer(modifier = Modifier.weight(1f))
            MultiplayerText(
                text = formatPlaybackTime(state.durationMs),
                style = typography.meta,
                color = colors.miniPlayerSecondaryContent,
            )
        }
    }
}

@Composable
private fun ControlButton(
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .size(SideControlHitWidth)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = MultiplayerTheme.colors.miniPlayerPrimaryContent,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMiniPlayerProgress(
    progress: Float,
    color: Color,
    isWaveAnimated: Boolean,
    wavePhase: Float,
    waveWidth: Float,
    amplitude: Float,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    if (clampedProgress <= 0f) return

    val progressWidth = size.width * clampedProgress
    val fillPath = Path().apply {
        moveTo(0f, 0f)

        if (isWaveAnimated && progressWidth > 1f) {
            val effectiveWaveWidth = waveWidth.coerceAtMost(progressWidth)
            val straightSectionEnd = (progressWidth - effectiveWaveWidth).coerceAtLeast(0f)
            lineTo(straightSectionEnd, 0f)

            val samples = 20
            for (index in 0..samples) {
                val y = size.height * (index / samples.toFloat())
                val normalized = index / samples.toFloat()
                val primary = sin(normalized * PI.toFloat() * ProgressWavePrimaryCycles + wavePhase)
                val harmonic = sin(
                    normalized * PI.toFloat() * ProgressWaveHarmonicCycles + wavePhase * 1.35f,
                ) * ProgressWaveHarmonicStrength
                val composite = ((primary + harmonic + 1f) / (2f + ProgressWaveHarmonicStrength))
                    .coerceIn(0f, 1f)
                val edgeOffset = composite * amplitude.coerceAtMost(progressWidth)
                val x = (progressWidth - edgeOffset).coerceAtLeast(0f)
                lineTo(x, y)
            }
        } else {
            lineTo(progressWidth, 0f)
            lineTo(progressWidth, size.height)
        }

        lineTo(0f, size.height)
        close()
    }

    drawPath(path = fillPath, color = color)
}

private fun formatPlaybackTime(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L).toInt()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Now Playing Strip Light Paused")
private fun NowPlayingStripLightPausedPreview() {
    MultiplayerPreview(contentAlignment = Alignment.TopCenter) {
        NowPlayingStrip(
            state = NowPlayingStripState(
                title = "Midnight Echoes",
                subtitle = "Neon Avenue",
                durationMs = 240_000L,
                currentPositionMs = 92_000L,
            ),
            onAction = {},
        )
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Now Playing Strip Light Playing")
private fun NowPlayingStripLightPlayingPreview() {
    MultiplayerPreview(contentAlignment = Alignment.TopCenter) {
        NowPlayingStrip(
            state = NowPlayingStripState(
                title = "Midnight Echoes",
                subtitle = "Neon Avenue",
                isPlaying = true,
                durationMs = 240_000L,
                currentPositionMs = 103_000L,
                progressFraction = 103_000f / 240_000f,
                displayedProgressFraction = 103_000f / 240_000f,
            ),
            onAction = {},
        )
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Now Playing Strip Dark Paused")
private fun NowPlayingStripDarkPausedPreview() {
    MultiplayerPreview(
        darkTheme = true,
        contentAlignment = Alignment.TopCenter,
    ) {
        NowPlayingStrip(
            state = NowPlayingStripState(
                title = "Midnight Echoes",
                subtitle = "Neon Avenue",
                durationMs = 240_000L,
                currentPositionMs = 82_000L,
                progressFraction = 82_000f / 240_000f,
                displayedProgressFraction = 82_000f / 240_000f,
            ),
            onAction = {},
        )
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Now Playing Strip Dark Playing")
private fun NowPlayingStripDarkPlayingPreview() {
    MultiplayerPreview(
        darkTheme = true,
        contentAlignment = Alignment.TopCenter,
    ) {
        NowPlayingStrip(
            state = NowPlayingStripState(
                title = "Midnight Echoes",
                subtitle = "Neon Avenue",
                isPlaying = true,
                durationMs = 240_000L,
                currentPositionMs = 121_000L,
                progressFraction = 121_000f / 240_000f,
                displayedProgressFraction = 121_000f / 240_000f,
            ),
            onAction = {},
        )
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Now Playing Strip Dark Seeking")
private fun NowPlayingStripDarkSeekingPreview() {
    MultiplayerPreview(
        darkTheme = true,
        contentAlignment = Alignment.TopCenter,
    ) {
        NowPlayingStrip(
            state = NowPlayingStripState(
                title = "Midnight Echoes",
                subtitle = "Neon Avenue",
                isPlaying = true,
                durationMs = 240_000L,
                currentPositionMs = 121_000L,
                progressFraction = 121_000f / 240_000f,
                isSeekInProgress = true,
                displayedProgressFraction = 0.74f,
            ),
            onAction = {},
        )
    }
}
