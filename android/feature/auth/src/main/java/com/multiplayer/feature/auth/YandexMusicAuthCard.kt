package com.multiplayer.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multiplayer.core.ui.components.MultiplayerSurface
import com.multiplayer.core.ui.components.MultiplayerText
import com.multiplayer.core.ui.theme.MultiplayerDesignSystem
import com.multiplayer.core.ui.theme.MultiplayerTheme

@Composable
fun YandexMusicAuthCard(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MultiplayerTheme.spacing
    val colors = MultiplayerTheme.colors
    val radius = MultiplayerTheme.radius
    val typography = MultiplayerTheme.typography
    val buttonTextColor = if (colors.background.luminance() > 0.5f) {
        colors.textPrimary
    } else {
        colors.textInverse
    }

    MultiplayerSurface(
        modifier = modifier,
        shape = RoundedCornerShape(radius.xLarge),
        color = colors.surfaceOverlay,
        shadowElevation = MultiplayerTheme.elevation.level3,
        border = BorderStroke(
            width = 1.dp,
            color = colors.textPrimary.copy(alpha = 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MultiplayerText(
                text = stringResource(R.string.auth_provider_title),
                style = typography.titleLarge.copy(
                    fontSize = 21.sp,
                    lineHeight = 24.sp,
                    letterSpacing = (-0.3).sp,
                ),
            )

            MultiplayerText(
                text = stringResource(R.string.auth_provider_description),
                style = typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = (-0.05).sp,
                ),
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(spacing.xxs))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.pill))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                colors.ctaGradientStart,
                                colors.ctaGradientEnd,
                            ),
                        ),
                    )
                    .clickable(enabled = !isLoading, onClick = onClick),
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = spacing.md)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.textInverse,
                            strokeWidth = 2.dp,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        MultiplayerText(
                            text = stringResource(R.string.auth_login_button),
                            style = typography.labelLarge,
                            color = buttonTextColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.alpha(if (isLoading) 0.8f else 1f),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Yandex Music Auth Card")
@Composable
private fun YandexMusicAuthCardPreview() {
    MultiplayerDesignSystem(darkTheme = true) {
        Box(
            modifier = Modifier
                .background(MultiplayerTheme.colors.background)
                .padding(MultiplayerTheme.spacing.lg),
        ) {
            YandexMusicAuthCard(
                isLoading = false,
                onClick = {},
            )
        }
    }
}
