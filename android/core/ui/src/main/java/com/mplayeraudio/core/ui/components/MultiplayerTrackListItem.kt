package com.mplayeraudio.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mplayeraudio.core.ui.model.MultiplayerTrackListItemState
import com.mplayeraudio.core.ui.preview.MultiplayerPreview
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

private val TrackListItemMinHeight = 48.dp
private val TrackListIndexMinWidth = 22.dp
private val TrackListTrailingMinWidth = 76.dp
private val TrackListTextSpacing = 2.dp
private val TrackListActiveTrailingSpacing = 18.dp

@Composable
fun MultiplayerTrackListItem(
    state: MultiplayerTrackListItemState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MultiplayerTheme.colors
    val spacing = MultiplayerTheme.spacing
    val typography = MultiplayerTheme.typography
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TrackListItemMinHeight)
            .then(clickableModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        MultiplayerText(
            text = state.trackPosition.toString(),
            modifier = Modifier.widthIn(min = TrackListIndexMinWidth),
            color = colors.miniPlayerSecondaryContent,
            style = typography.label.copy(),
            textAlign = TextAlign.End,
            maxLines = 1,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TrackListTextSpacing),
        ) {
            MultiplayerText(
                text = state.title,
                color = colors.miniPlayerPrimaryContent,
                style = typography.compactTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MultiplayerText(
                text = state.artist,
                color = colors.miniPlayerSecondaryContent,
                style = typography.meta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TrackListItemTrailing(
            state = state,
            activeColor = colors.miniPlayerPrimaryContent,
        )
    }
}

@Composable
private fun TrackListItemTrailing(
    state: MultiplayerTrackListItemState,
    activeColor: Color,
) {
    Box(
        modifier = Modifier.widthIn(min = TrackListTrailingMinWidth),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(TrackListActiveTrailingSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isActive) {
                MultiplayerEqualizerIndicator(color = activeColor)
            }
            MultiplayerText(
                text = state.duration,
                color = activeColor,
                style = MultiplayerTheme.typography.meta,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MultiplayerTrackListItemPreviewContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        MultiplayerTrackListItem(
            state = MultiplayerTrackListItemState(
                title = "Midnight Drive",
                artist = "The Northern Lights",
                duration = "4:11",
                trackPosition = 1,
                isActive = true,
            ),
        )
        MultiplayerTrackListItem(
            state = MultiplayerTrackListItemState(
                title = "Neon Skyline",
                artist = "Tokyo After Dark",
                duration = "4:11",
                trackPosition = 2,
                isActive = false,
            ),
        )
        MultiplayerTrackListItem(
            state = MultiplayerTrackListItemState(
                title = "City Pulse",
                artist = "Mira Lane",
                duration = "3:58",
                trackPosition = 3,
                isActive = false,
            ),
        )
        MultiplayerTrackListItem(
            state = MultiplayerTrackListItemState(
                title = "Slow Motion",
                artist = "Polar Kids",
                duration = "5:02",
                trackPosition = 4,
                isActive = false,
            ),
        )
    }
}

@Preview(showBackground = true, name = "Track list item - light")
@Composable
private fun MultiplayerTrackListItemLightPreview() {
    MultiplayerPreview(
        contentAlignment = Alignment.TopStart,
    ) {
        MultiplayerCardSurface(
            style = MultiplayerCardSurfaceStyle.Surface2,
        ) {
            MultiplayerTrackListItemPreviewContent()
        }
    }
}

@Preview(showBackground = true, name = "Track list item - dark")
@Composable
private fun MultiplayerTrackListItemDarkPreview() {
    MultiplayerPreview(
        darkTheme = true,
        contentAlignment = Alignment.TopStart,
    ) {
        MultiplayerCardSurface(
            style = MultiplayerCardSurfaceStyle.Surface2,
        ) {
            MultiplayerTrackListItemPreviewContent()
        }
    }
}
