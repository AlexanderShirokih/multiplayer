package com.multiplayer.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember

@Composable
fun AuthWelcomeRoute(
    onEvent: (AuthWelcomeEvent) -> Unit = {},
    viewModel: AuthWelcomeViewModel = remember { AuthWelcomeViewModel() },
) {
    val state = viewModel.state.collectAsState()

    AuthWelcomeScreen(
        state = state.value,
        onEvent = { event ->
            viewModel.onEvent(event)
            onEvent(event)
        },
    )
}
