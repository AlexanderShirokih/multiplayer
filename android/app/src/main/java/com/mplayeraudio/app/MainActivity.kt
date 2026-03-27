package com.mplayeraudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mplayeraudio.core.ui.components.MultiplayerSurface
import com.mplayeraudio.core.ui.theme.MultiplayerDesignSystem
import com.mplayeraudio.core.ui.theme.MultiplayerTheme
import com.mplayeraudio.feature.auth.AuthWelcomeRoute
import com.mplayeraudio.feature.player.PlayerRoute
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MultiPlayerApp()
        }
    }
}

@Composable
private fun MultiPlayerApp() {
    val observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase = koinInject()
    val authorizedProvider by observeAuthorizedMusicProvider()
        .collectAsStateWithLifecycle(
            initialValue = observeAuthorizedMusicProvider.currentAuthorizedProvider(),
        )

    MultiplayerDesignSystem(darkTheme = true) {
        MultiplayerSurface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = MultiplayerTheme.colors.background,
        ) {
            Crossfade(
                targetState = authorizedProvider != null,
                label = "app-screen",
            ) { isAuthorized ->
                if (isAuthorized) {
                    PlayerRoute()
                } else {
                    AuthWelcomeRoute()
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen() {
    AuthWelcomeRoute()
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    MultiplayerDesignSystem(darkTheme = true) {
        WelcomeScreen()
    }
}
