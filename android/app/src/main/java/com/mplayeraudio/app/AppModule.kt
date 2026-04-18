package com.mplayeraudio.app

import com.mplayeraudio.core.data.DefaultMusicLibrary
import com.mplayeraudio.core.domain.musiclibrary.MusicLibrary
import com.mplayeraudio.core.domain.musiclibrary.MusicProvider
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun appModule() = module {
    factory<ObserveAuthorizedMusicProviderUseCase> {
        DefaultObserveAuthorizedMusicProviderUseCase(
            repository = get(),
        )
    }
    single<MusicLibrary> {
        DefaultMusicLibrary(
            providers = getAll<MusicProvider>().toSet(),
        )
    }
    viewModel {
        AppRootViewModel(
            observeAuthorizedMusicProvider = get(),
        )
    }
}
