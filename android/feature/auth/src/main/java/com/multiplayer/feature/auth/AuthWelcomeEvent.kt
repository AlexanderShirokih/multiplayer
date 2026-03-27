package com.multiplayer.feature.auth

sealed interface AuthWelcomeEvent {
    data class LoginClicked(val provider: MusicProvider) : AuthWelcomeEvent
}
