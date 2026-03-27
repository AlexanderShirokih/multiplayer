package com.multiplayer.feature.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthWelcomeViewModel {

    private val _state = MutableStateFlow(AuthWelcomeState())
    val state: StateFlow<AuthWelcomeState> = _state.asStateFlow()

    fun onEvent(event: AuthWelcomeEvent) {
        when (event) {
            is AuthWelcomeEvent.LoginClicked -> Unit
        }
    }
}
