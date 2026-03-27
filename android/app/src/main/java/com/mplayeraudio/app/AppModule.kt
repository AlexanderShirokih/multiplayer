package com.mplayeraudio.app

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun appModule() = module {
    factory<ObserveAuthorizedMusicProviderUseCase> {
        DefaultObserveAuthorizedMusicProviderUseCase(
            repository = get(),
        )
    }
    viewModel {
        AppRootViewModel(
            observeAuthorizedMusicProvider = get(),
        )
    }
}
