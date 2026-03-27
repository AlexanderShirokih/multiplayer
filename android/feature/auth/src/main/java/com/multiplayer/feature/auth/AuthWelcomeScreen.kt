package com.multiplayer.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multiplayer.core.ui.components.MultiplayerText
import com.multiplayer.core.ui.theme.MultiplayerDesignSystem
import com.multiplayer.core.ui.theme.MultiplayerTheme

@Composable
fun AuthWelcomeScreen(
    state: AuthWelcomeState,
    onEvent: (AuthWelcomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MultiplayerTheme.colors
    val yandexProvider = state.availableMusicProviders.firstOrNull { it.type == MusicProvider.YandexMusic.type }

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
        AuthWelcomeBackgroundDecor(
            modifier = Modifier
                .fillMaxSize(),
        )

        AuthWelcomeTitleBlock(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = maxWidth * 0.085f)
                .offset(y = maxHeight * 0.48f),
        )

        if (yandexProvider != null) {
            YandexMusicAuthCard(
                isLoading = state.isLoading,
                onClick = { onEvent(AuthWelcomeEvent.LoginClicked(yandexProvider)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = maxWidth * 0.085f,
                        end = maxWidth * 0.085f,
                        bottom = maxHeight * 0.05f,
                    ),
            )
        }
    }
}

@Composable
private fun BoxScope.AuthWelcomeBackgroundDecor(
    modifier: Modifier = Modifier,
) {
    val colors = MultiplayerTheme.colors

    Box(modifier = modifier) {
        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-18).dp)
                .blur(
                    radius = 84.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 340.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualPrimary.copy(alpha = 0.98f),
                    colors.brandVisualPrimary.copy(alpha = 0.58f),
                    colors.brandVisualPrimaryContainer.copy(alpha = 0.22f),
                    Color.Transparent,
                ),
            ),
            alpha = 1f,
        )

        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 18.dp, y = 36.dp)
                .blur(
                    radius = 28.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 168.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualPrimaryMuted.copy(alpha = 0.9f),
                    colors.brandVisualPrimaryContainer.copy(alpha = 0.42f),
                    Color.Transparent,
                ),
            ),
            alpha = 0.96f,
        )

        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-40).dp, y = 158.dp)
                .blur(
                    radius = 58.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 232.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualSecondary.copy(alpha = 0.88f),
                    colors.brandVisualSecondary.copy(alpha = 0.34f),
                    Color.Transparent,
                ),
            ),
            alpha = 0.92f,
        )

        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 4.dp, y = 190.dp)
                .blur(
                    radius = 18.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 96.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualSecondary.copy(alpha = 0.74f),
                    colors.brandVisualSecondary.copy(alpha = 0.2f),
                    Color.Transparent,
                ),
            ),
            alpha = 0.8f,
        )

        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 18.dp, y = 42.dp)
                .blur(
                    radius = 58.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            size = 320.dp,
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.brandVisualPrimaryContainer.copy(alpha = 0.34f),
                    Color.Transparent,
                ),
            ),
            alpha = 0.92f,
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
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
                color = Color(0xFF08101F).copy(alpha = 0.36f),
            )
        }
    }
}

@Composable
private fun DecorativeGlow(
    size: Dp,
    brush: Brush,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .background(
                brush = brush,
                shape = CircleShape,
            ),
    )
}

@Composable
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
            state = AuthWelcomeState(),
            onEvent = {},
        )
    }
}
