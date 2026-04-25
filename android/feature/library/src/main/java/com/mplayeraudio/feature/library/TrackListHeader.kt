package com.mplayeraudio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mplayeraudio.core.ui.preview.MultiplayerPreview
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import com.mplayeraudio.core.ui.components.MultiplayerText
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check

@Composable
internal fun TrackListHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
    isEditing: Boolean = false,
) {
    val colors = MultiplayerTheme.colors
    val spacing = MultiplayerTheme.spacing
    val typography = MultiplayerTheme.typography
    val isLightTheme = colors.background.luminance() > TrackListLightThemeLuminanceThreshold
    val primaryTextColor = if (isLightTheme) {
        colors.miniPlayerPrimaryContent
    } else {
        colors.textPrimary
    }
    val secondaryTextColor = if (isLightTheme) {
        colors.miniPlayerSecondaryContent
    } else {
        colors.textSecondary
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackListBackButton(onClick = onBack)
            
            if (onEditClick != null) {
                Box(
                    modifier = Modifier
                        .size(TrackListScreenMetrics.backButtonHeight)
                        .background(
                            color = colors.surfacePrimary,
                            shape = RoundedCornerShape(MultiplayerTheme.radius.pill),
                        )
                        .clickable(onClick = onEditClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                        contentDescription = if (isEditing) {
                            stringResource(R.string.playlist_action_done)
                        } else {
                            stringResource(R.string.playlist_action_edit)
                        },
                        tint = primaryTextColor,
                        modifier = Modifier.size(TrackListScreenMetrics.backButtonIconSize),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(spacing.xl))
        MultiplayerText(
            text = title,
            style = typography.pageTitle,
            color = primaryTextColor,
        )
        subtitle?.let { value ->
            Spacer(modifier = Modifier.height(spacing.sm))
            MultiplayerText(
                text = value,
                style = typography.label,
                color = secondaryTextColor,
            )
        }
    }
}

@Composable
private fun TrackListBackButton(
    onClick: () -> Unit,
) {
    val colors = MultiplayerTheme.colors
    val isLightTheme = colors.background.luminance() > TrackListLightThemeLuminanceThreshold
    val surfaceRamp = if (isLightTheme) {
        colors.miniPlayerGradient
    } else {
        colors.surface2Gradient
    }
    val iconColor = if (isLightTheme) {
        colors.miniPlayerPrimaryContent
    } else {
        colors.textPrimary
    }

    Box(
        modifier = Modifier
            .size(
                width = TrackListScreenMetrics.backButtonWidth,
                height = TrackListScreenMetrics.backButtonHeight,
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(surfaceRamp.start, surfaceRamp.end),
                ),
                shape = RoundedCornerShape(MultiplayerTheme.radius.pill),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.playlist_detail_back),
                tint = iconColor,
                modifier = Modifier.size(TrackListScreenMetrics.backButtonIconSize),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 406, heightDp = 220, name = "Track List Header - Light")
@Composable
private fun TrackListHeaderLightPreview() {
    MultiplayerPreview(darkTheme = false) {
        TrackListHeader(
            title = "Моя фонотека",
            subtitle = "4 трека • 24 ч 12 м",
            onBack = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, widthDp = 406, heightDp = 220, name = "Track List Header - Dark")
@Composable
private fun TrackListHeaderDarkPreview() {
    MultiplayerPreview(darkTheme = true) {
        TrackListHeader(
            title = "Моя фонотека",
            subtitle = "4 трека • 24 ч 12 м",
            onBack = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
