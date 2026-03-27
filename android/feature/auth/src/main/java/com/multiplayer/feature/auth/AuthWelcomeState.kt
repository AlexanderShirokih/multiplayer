package com.multiplayer.feature.auth


data class AuthWelcomeState(
    val isLoading: Boolean = false,
    val availableMusicProviders: List<MusicProvider> = listOf(MusicProvider.YandexMusic),
)
