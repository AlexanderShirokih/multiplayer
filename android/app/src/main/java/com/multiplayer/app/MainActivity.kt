package com.multiplayer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.multiplayer.core.ui.components.MultiplayerSurface
import com.multiplayer.core.ui.components.MultiplayerText
import com.multiplayer.core.ui.preview.MultiplayerPreview
import com.multiplayer.core.ui.theme.MultiplayerDesignSystem
import com.multiplayer.core.ui.theme.MultiplayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MultiPlayerApp()
        }
    }
}

@Composable
private fun MultiPlayerApp() {
    MultiplayerDesignSystem {
        MultiplayerSurface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = MultiplayerTheme.colors.background,
        ) {
            WelcomeScreen()
        }
    }
}

@Composable
private fun WelcomeScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MultiplayerText(text = "MultiPlayer")
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    MultiplayerPreview(contentAlignment = Alignment.Center) {
        WelcomeScreen()
    }
}
