package com.multiplayer.feature.auth.yamusic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class YandexMusicAuthCardState(
    val isLoading: Boolean = false,
)

class YandexMusicAuthCardViewModel {

    private val _state = MutableStateFlow(YandexMusicAuthCardState())
    val state: StateFlow<YandexMusicAuthCardState> = _state.asStateFlow()

    fun onLoginClicked() {
        // OAuth / Yandex Music flow
    }
}
