package com.mplayeraudio.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.mplayeraudio.core.ui.components.MultiplayerSurface
import com.mplayeraudio.core.ui.components.MultiplayerText
import com.mplayeraudio.core.ui.preview.MultiplayerPreview
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

@Composable
fun PlayerPlaceholderScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
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
                    text = stringResource(R.string.player_placeholder_title),
                    style = MultiplayerTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                MultiplayerText(
                    text = stringResource(R.string.player_placeholder_description),
                    style = MultiplayerTheme.typography.bodyMedium,
                    color = MultiplayerTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
private fun PlayerPlaceholderScreenPreview() {
    MultiplayerPreview(contentAlignment = Alignment.Center) {
        PlayerPlaceholderScreen()
    }
}
