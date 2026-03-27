package com.mplayeraudio.feature.auth.yamusic

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

fun yandexMusicAuthModule() = module {
    factory { StartYandexAuthorizationUseCase(get()) }
    factory { CompleteYandexAuthorizationUseCase(get()) }
    factory { ObserveYandexSessionUseCase(get()) }
    factory { ObserveYandexAuthStatusUseCase(get()) }
    viewModel {
        YandexMusicAuthCardViewModel(
            startYandexAuthorization = get(),
            observeYandexSession = get(),
            observeYandexAuthStatus = get(),
        )
    }
}
