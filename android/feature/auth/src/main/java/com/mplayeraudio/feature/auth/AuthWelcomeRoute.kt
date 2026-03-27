package com.mplayeraudio.feature.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import com.mplayeraudio.feature.auth.yamusic.YandexMusicAuthCard
import com.mplayeraudio.feature.auth.yamusic.YandexMusicAuthCardEffect
import com.mplayeraudio.feature.auth.yamusic.YandexMusicAuthCardViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun AuthWelcomeRoute(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val yandexMusicAuthViewModel: YandexMusicAuthCardViewModel = koinViewModel()
    val availableMusicProviders = listOf(MusicProvider.YandexMusic)

    LaunchedEffect(yandexMusicAuthViewModel, context) {
        yandexMusicAuthViewModel.effects.collectLatest { effect ->
            when (effect) {
                is YandexMusicAuthCardEffect.OpenExternalAuth -> {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                        }
                        context.startActivity(intent)
                    }.onFailure { throwable ->
                        if (throwable is ActivityNotFoundException) {
                            yandexMusicAuthViewModel.onBrowserLaunchFailed()
                        } else {
                            throw throwable
                        }
                    }
                }

                is YandexMusicAuthCardEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
