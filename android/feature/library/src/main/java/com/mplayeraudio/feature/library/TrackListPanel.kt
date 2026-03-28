package com.mplayeraudio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.mplayeraudio.core.ui.components.MultiplayerText
import com.mplayeraudio.core.ui.model.MultiplayerTrackListItemState
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

@Composable
internal fun TrackListPanel(
    tracks: List<MultiplayerTrackListItemState>,
    onTrackClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MultiplayerTheme.colors
    val spacing = MultiplayerTheme.spacing
    val isLightTheme = colors.background.luminance() > TrackListLightThemeLuminanceThreshold
    val surfaceRamp = if (isLightTheme) {
        colors.miniPlayerGradient
    } else {
        colors.surface2Gradient
    }
    val borderColor = if (isLightTheme) {
        colors.background
    } else {
        colors.borderSubtle
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(surfaceRamp.start, surfaceRamp.end),
                ),
                shape = RoundedCornerShape(MultiplayerTheme.radius.large),
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(MultiplayerTheme.radius.large),
            ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .heightIn(min = TrackListScreenMetrics.trackPanelMinHeight),
            contentPadding = PaddingValues(
                start = TrackListScreenMetrics.trackItemHorizontalSpacing,
                end = TrackListScreenMetrics.trackItemHorizontalSpacing,
                top = spacing.xl,
                bottom = spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            itemsIndexed(
                items = tracks,
                key = { _, track -> "${track.trackPosition}-${track.title}" },
            ) { index, track ->
                TrackListItemRow(
                    state = track,
                    onClick = { onTrackClick(index) },
                )
            }
        }
    }
}

@Composable
private fun TrackListItemRow(
    state: MultiplayerTrackListItemState,
    onClick: () -> Unit,
) {
    val colors = MultiplayerTheme.colors
    val typography = MultiplayerTheme.typography
    val isLightTheme = colors.background.luminance() > TrackListLightThemeLuminanceThreshold
    val primaryTextColor = if (isLightTheme) {
        colors.miniPlayerPrimaryContent
    } else {
        colors.surfaceContentPrimary
    }
    val secondaryTextColor = if (isLightTheme) {
        colors.miniPlayerSecondaryContent
    } else {
        colors.surfaceContentSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TrackListScreenMetrics.trackItemMinHeight)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MultiplayerText(
            text = state.trackPosition.toString(),
            modifier = Modifier.width(TrackListScreenMetrics.trackIndexWidth),
            style = typography.label,
            color = secondaryTextColor,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TrackListScreenMetrics.trackItemTextSpacing),
        ) {
            MultiplayerText(
                text = state.title,
                style = typography.compactTitle,
                color = primaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MultiplayerText(
                text = state.artist,
                style = typography.meta,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(TrackListScreenMetrics.activeIndicatorSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isActive) {
                TrackListActiveIndicator(color = primaryTextColor)
            }

            MultiplayerText(
                text = state.duration,
                style = typography.label,
                color = primaryTextColor,
            )
        }
    }
}

@Composable
private fun TrackListActiveIndicator(
    color: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TrackListScreenMetrics.activeIndicatorBarGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackListActiveIndicatorBar(
            color = color,
            height = TrackListScreenMetrics.activeIndicatorShortBarHeight,
        )
        TrackListActiveIndicatorBar(
            color = color,
            height = TrackListScreenMetrics.activeIndicatorTallBarHeight,
        )
        TrackListActiveIndicatorBar(
            color = color,
            height = TrackListScreenMetrics.activeIndicatorShortBarHeight,
        )
    }
}

@Composable
private fun TrackListActiveIndicatorBar(
    color: Color,
    height: Dp,
) {
    Box(
        modifier = Modifier
            .width(TrackListScreenMetrics.activeIndicatorBarWidth)
            .height(height)
            .background(
                color = color,
                shape = CircleShape,
            ),
    )
}
