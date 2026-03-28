package com.mplayeraudio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.mplayeraudio.core.ui.components.MultiplayerBrandBackgroundDecor
import com.mplayeraudio.core.ui.components.MultiplayerSurface
import com.mplayeraudio.core.ui.components.MultiplayerText
import com.mplayeraudio.core.ui.preview.MultiplayerPreview
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

@Composable
fun PlaylistDetailRoute(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaylistDetailPlaceholderScreen(
        title = title,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun PlaylistDetailPlaceholderScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MultiplayerTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.backgroundGradient.start,
                        colors.backgroundGradient.end,
                    ),
                ),
            ),
    ) {
        MultiplayerBrandBackgroundDecor(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = MultiplayerTheme.spacing.lg,
                    vertical = MultiplayerTheme.spacing.xl,
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MultiplayerSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MultiplayerTheme.radius.xLarge),
                tonalElevation = MultiplayerTheme.elevation.level2,
                shadowElevation = MultiplayerTheme.elevation.level1,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = MultiplayerTheme.spacing.xl,
                        vertical = MultiplayerTheme.spacing.xxxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MultiplayerTheme.spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MultiplayerText(
                        text = title,
                        style = MultiplayerTheme.typography.title,
                        textAlign = TextAlign.Center,
                    )
                    MultiplayerText(
                        text = stringResource(R.string.playlist_detail_placeholder),
                        style = MultiplayerTheme.typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onBack) {
                        MultiplayerText(
                            text = stringResource(R.string.playlist_detail_back),
                            style = MultiplayerTheme.typography.label,
                            color = colors.accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun PlaylistDetailPlaceholderScreenPreview() {
    MultiplayerPreview(contentAlignment = Alignment.Center) {
        PlaylistDetailPlaceholderScreen(
            title = "Плейлист",
            onBack = {},
        )
    }
}
