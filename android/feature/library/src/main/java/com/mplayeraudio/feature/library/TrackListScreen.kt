@file:Suppress("MagicNumber")

package com.mplayeraudio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mplayeraudio.core.player.NowPlayingStrip
import com.mplayeraudio.core.player.NowPlayingStripAction
import com.mplayeraudio.core.player.NowPlayingStripState
import com.mplayeraudio.core.ui.components.MultiplayerBrandBackgroundDecor
import com.mplayeraudio.core.ui.model.MultiplayerTrackListItemState
import com.mplayeraudio.core.ui.theme.MultiplayerDesignSystem
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

internal const val TrackListLightThemeLuminanceThreshold = 0.5f

@Composable
fun TrackListScreen(
    state: TrackListScreenState,
    onNowPlayingAction: (NowPlayingStripAction) -> Unit,
    onTrackClick: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MultiplayerTheme.colors
    val spacing = MultiplayerTheme.spacing
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            colors.backgroundGradient.start,
            colors.backgroundGradient.end,
        ),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
    ) {
        MultiplayerBrandBackgroundDecor(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = spacing.xxxl),
        ) {
            TrackListHeader(
                title = state.title,
                subtitle = state.subtitle,
                onBack = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md),
            )

            Spacer(modifier = Modifier.height(spacing.xl))

            NowPlayingStrip(
                state = state.nowPlaying,
                onAction = onNowPlayingAction,
                showBorder = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MultiplayerTheme.spacing.md),
            )

            Spacer(modifier = Modifier.height(spacing.xl))

            TrackListPanel(
                tracks = state.tracks,
                onTrackClick = onTrackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = MultiplayerTheme.spacing.md)
                    .padding(bottom = MultiplayerTheme.spacing.md),
            )
        }
    }
}

internal object TrackListScreenMetrics {
    val backButtonWidth = 55.dp
    val backButtonHeight = 34.dp
    val backButtonIconSize = 18.dp
    val trackPanelMinHeight = 420.dp
    val trackItemHorizontalSpacing = 24.dp
    val trackItemMinHeight = 56.dp
    val trackItemTextSpacing = 2.dp
    val trackIndexWidth = 24.dp
    val activeIndicatorSpacing = 16.dp
    val activeIndicatorBarWidth = 4.dp
    val activeIndicatorBarGap = 4.dp
    val activeIndicatorTallBarHeight = 28.dp
    val activeIndicatorShortBarHeight = 20.dp
}

private fun previewTrackListState(): TrackListScreenState {
    return TrackListScreenState(
        title = "Моя фонотека",
        subtitle = "4 трека • 24 ч 12 м",
        nowPlaying = NowPlayingStripState(
            title = "Midnight Drive",
            subtitle = "The Northern Lights",
            isPlaying = true,
            durationMs = 251_000L,
            currentPositionMs = 130_000L,
            progressFraction = 130_000f / 251_000f,
            displayedProgressFraction = 130_000f / 251_000f,
        ),
        tracks = listOf(
            MultiplayerTrackListItemState(
                title = "Midnight Drive",
                artist = "The Northern Lights",
                duration = "4:11",
                trackPosition = 1,
                isActive = true,
            ),
            MultiplayerTrackListItemState(
                title = "Neon Skyline",
                artist = "Tokyo After Dark",
                duration = "4:11",
                trackPosition = 2,
            ),
            MultiplayerTrackListItemState(
                title = "City Pulse",
                artist = "Mira Lane",
                duration = "3:58",
                trackPosition = 3,
            ),
            MultiplayerTrackListItemState(
                title = "Slow Motion",
                artist = "Polar Kids",
                duration = "5:02",
                trackPosition = 4,
            ),
        ),
    )
}

@Preview(showBackground = true, widthDp = 406, heightDp = 874, name = "Track List Screen - Light")
@Composable
private fun TrackListScreenLightPreview() {
    MultiplayerDesignSystem(darkTheme = false) {
        TrackListScreen(
            state = previewTrackListState(),
            onNowPlayingAction = {},
            onTrackClick = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 406, heightDp = 874, name = "Track List Screen - Dark")
@Composable
private fun TrackListScreenDarkPreview() {
    MultiplayerDesignSystem(darkTheme = true) {
        TrackListScreen(
            state = previewTrackListState(),
            onNowPlayingAction = {},
            onTrackClick = {},
            onBack = {},
        )
    }
}
