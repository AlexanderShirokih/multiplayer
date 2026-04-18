package com.mplayeraudio.feature.auth

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mplayeraudio.feature.auth.yamusic.YandexMusicAuthCard
import com.mplayeraudio.feature.auth.yamusic.YandexMusicAuthCardEffect
import com.mplayeraudio.feature.auth.yamusic.YandexMusicAuthCardViewModel
import com.mplayeraudio.feature.auth.yamusic.YandexMusicAuthWebViewScreen
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthWelcomeRoute(
    onListenLocalClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val yandexMusicAuthViewModel: YandexMusicAuthCardViewModel = koinViewModel()
    val yandexMusicAuthState by yandexMusicAuthViewModel.state.collectAsStateWithLifecycle()
    val availableMusicProviders = listOf(MusicProvider.YandexMusic)

    LaunchedEffect(yandexMusicAuthViewModel, context) {
        yandexMusicAuthViewModel.effects.collectLatest { effect ->
            when (effect) {
                is YandexMusicAuthCardEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AuthWelcomeScreen(
            loginContent = {
                for (provider in availableMusicProviders) {
                    when (provider.type) {
                        MusicProvider.YandexMusic.type -> YandexMusicAuthCard(
                            state = yandexMusicAuthState,
                            onClick = yandexMusicAuthViewModel::onLoginClicked,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            onListenLocalClick = onListenLocalClick,
            modifier = Modifier.fillMaxSize(),
        )

        yandexMusicAuthState.authorizationRequest?.let { request ->
            YandexMusicAuthWebViewScreen(
                request = request,
                isAuthorizing = yandexMusicAuthState.isLoading,
                onCloseClick = yandexMusicAuthViewModel::onAuthorizationCancelled,
                onAuthorizationCallback = yandexMusicAuthViewModel::onAuthorizationCallbackReceived,
                onLaunchFailure = yandexMusicAuthViewModel::onAuthorizationLaunchFailed,
            )
        }
    }
}
