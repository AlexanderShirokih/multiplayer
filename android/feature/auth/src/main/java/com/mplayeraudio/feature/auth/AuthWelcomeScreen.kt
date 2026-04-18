package com.mplayeraudio.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.mplayeraudio.core.ui.components.MultiplayerBrandBackgroundDecor
import com.mplayeraudio.core.ui.components.MultiplayerText
import com.mplayeraudio.core.ui.theme.MultiplayerDesignSystem
import com.mplayeraudio.core.ui.theme.MultiplayerTheme
import com.mplayeraudio.feature.auth.yamusic.YandexMusicAuthCard
import com.mplayeraudio.feature.auth.yamusic.YandexMusicAuthCardState

@Composable
fun AuthWelcomeScreen(
    loginContent: @Composable ColumnScope.() -> Unit,
    onListenLocalClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MultiplayerTheme.colors
    val spacing = MultiplayerTheme.spacing

    BoxWithConstraints(
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
        MultiplayerBrandBackgroundDecor(
            modifier = Modifier
                .fillMaxSize(),
        )

        AuthWelcomeBackgroundCurve(
            modifier = Modifier.fillMaxSize(),
        )

        AuthWelcomeTitleBlock(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = maxWidth * 0.085f)
                .offset(y = maxHeight * 0.48f),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = maxWidth * 0.085f,
                    end = maxWidth * 0.085f,
                    bottom = maxHeight * 0.05f,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            loginContent()

            TextButton(
                onClick = onListenLocalClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                MultiplayerText(
                    text = stringResource(R.string.auth_welcome_listen_local),
                    style = MultiplayerTheme.typography.label,
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
@Suppress("MagicNumber")
private fun AuthWelcomeBackgroundCurve(
    modifier: Modifier = Modifier,
) {
    val colors = MultiplayerTheme.colors
    val isLightTheme = colors.background.luminance() > 0.5f
    val curveColor = if (isLightTheme) {
        colors.brandVisualPrimary.copy(alpha = 0.20f)
    } else {
        colors.brandVisualPrimaryContainer.copy(alpha = 0.18f)
    }

    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.444f)
            cubicTo(
                size.width * 0.17f,
                size.height * 0.434f,
                size.width * 0.288f,
                size.height * 0.402f,
                size.width * 0.454f,
                size.height * 0.346f,
            )
            cubicTo(
                size.width * 0.58f,
                size.height * 0.31f,
                size.width * 0.722f,
                size.height * 0.252f,
                size.width,
                size.height * 0.224f,
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = path,
            color = curveColor,
        )
    }
}

@Composable
@Suppress("MagicNumber")
private fun AuthWelcomeTitleBlock(
    modifier: Modifier = Modifier,
) {
    val spacing = MultiplayerTheme.spacing
    val colors = MultiplayerTheme.colors
    val typography = MultiplayerTheme.typography

    Column(modifier = modifier) {
        MultiplayerText(
            text = stringResource(R.string.auth_welcome_app_name),
            style = typography.pageTitle.copy(
                fontSize = 43.sp,
                lineHeight = 45.sp,
                letterSpacing = (-1.6).sp,
            ),
            color = colors.textPrimary,
        )

        MultiplayerText(
            text = stringResource(R.string.auth_welcome_title),
            style = typography.title.copy(
                letterSpacing = (-0.35).sp,
            ),
            color = colors.textPrimary.copy(alpha = 0.92f),
            modifier = Modifier
                .padding(top = spacing.xxs)
                .fillMaxWidth(0.78f),
        )
    }
}

@Preview(showBackground = true, name = "Auth Welcome Screen")
@Composable
private fun AuthWelcomeScreenPreview() {
    MultiplayerDesignSystem(darkTheme = true) {
        AuthWelcomeScreen(
            loginContent = {
                YandexMusicAuthCard(
                    state = YandexMusicAuthCardState(),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            onListenLocalClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Auth Welcome Screen Light")
@Composable
private fun AuthWelcomeScreenLightPreview() {
    MultiplayerDesignSystem(darkTheme = false) {
        AuthWelcomeScreen(
            loginContent = {
                YandexMusicAuthCard(
                    state = YandexMusicAuthCardState(),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            onListenLocalClick = {},
        )
    }
}
