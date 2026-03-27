package com.multiplayer.core.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.multiplayer.core.ui.components.MultiplayerSurface
import com.multiplayer.core.ui.components.MultiplayerText
import com.multiplayer.core.ui.theme.MultiplayerDesignSystem
import com.multiplayer.core.ui.theme.MultiplayerTheme

@Composable
fun MultiplayerPreview(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    MultiplayerDesignSystem {
        MultiplayerSurface(
            modifier = modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = MultiplayerTheme.colors.background,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MultiplayerTheme.spacing.lg),
                contentAlignment = contentAlignment,
                content = content,
            )
        }
    }
}

@Preview(showBackground = true, name = "Multiplayer Preview Text")
@Composable
private fun MultiplayerPreviewTextPreview() {
    MultiplayerPreview(contentAlignment = Alignment.Center) {
        MultiplayerText(text = "MultiPlayer")
    }
}

@Preview(showBackground = true, name = "Multiplayer Preview Surface")
@Composable
private fun MultiplayerPreviewSurfacePreview() {
    MultiplayerPreview(contentAlignment = Alignment.Center) {
        MultiplayerSurface(
            shape = RoundedCornerShape(MultiplayerTheme.radius.large),
            tonalElevation = MultiplayerTheme.elevation.level2,
            shadowElevation = MultiplayerTheme.elevation.level1,
        ) {
            Box(modifier = Modifier.padding(MultiplayerTheme.spacing.lg)) {
                MultiplayerText(text = "Now Playing")
            }
        }
    }
}
