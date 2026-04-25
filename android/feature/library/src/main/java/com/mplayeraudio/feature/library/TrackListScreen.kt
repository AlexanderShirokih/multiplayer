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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
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
    onAddTrackClick: () -> Unit,
    onDeletePlaylistClick: () -> Unit,
    onEditClick: () -> Unit,
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
                subtitle = stringResource(R.string.track_list_subtitle, state.trackCount),
                onBack = onBack,
                onEditClick = if (state.isEditable) onEditClick else null,
                isEditing = state.isEditing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.md),
            )

            Spacer(modifier = Modifier.height(spacing.xl))

            if (state.nowPlaying != null) {
                NowPlayingStrip(
                    state = state.nowPlaying,
                    onAction = onNowPlayingAction,
                    showBorder = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MultiplayerTheme.spacing.md),
                )

                Spacer(modifier = Modifier.height(spacing.xl))
            }

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

        if (state.isEditing) {
            EditPlaylistAddBar(
                onAddClick = onAddTrackClick,
                onDeleteClick = onDeletePlaylistClick,
                isDeleting = state.isDeletingPlaylist,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
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
}

private fun previewTrackListState(): TrackListScreenState {
    return TrackListScreenState(
        title = "Моя фонотека",
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
            onAddTrackClick = {},
            onDeletePlaylistClick = {},
            onEditClick = {},
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
            onAddTrackClick = {},
            onDeletePlaylistClick = {},
            onEditClick = {},
            onBack = {},
        )
    }
}
