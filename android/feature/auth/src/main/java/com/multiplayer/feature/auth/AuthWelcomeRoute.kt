package com.multiplayer.feature.auth

import androidx.compose.foundation.layout.fillMaxWidth
import com.multiplayer.feature.auth.yamusic.YandexMusicAuthCard
import com.multiplayer.feature.auth.yamusic.YandexMusicAuthCardViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun AuthWelcomeRoute(
    modifier: Modifier = Modifier,
) {
    val yandexMusicAuthViewModel = remember { YandexMusicAuthCardViewModel() }
    val availableMusicProviders = listOf(MusicProvider.YandexMusic)

    AuthWelcomeScreen(
        loginContent = {
            for (provider in availableMusicProviders) {
                when (provider.type) {
                    MusicProvider.YandexMusic.type -> YandexMusicAuthCard(
                        viewModel = yandexMusicAuthViewModel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        modifier = modifier,
    )
}
