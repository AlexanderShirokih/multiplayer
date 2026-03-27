package com.mplayeraudio.feature.auth

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
                        colors.backgroundGradientStart,
                        colors.backgroundGradientEnd,
                    ),
                ),
            ),
    ) {
        MultiplayerBrandBackgroundDecor(
            modifier = Modifier
                .fillMaxSize(),
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
        }
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
            style = typography.displayLarge.copy(
                fontSize = 43.sp,
                lineHeight = 45.sp,
                letterSpacing = (-1.6).sp,
            ),
            color = colors.textPrimary,
        )

        MultiplayerText(
            text = stringResource(R.string.auth_welcome_title),
            style = typography.headlineLarge.copy(
                fontSize = 20.sp,
                lineHeight = 24.sp,
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
        )
    }
}
