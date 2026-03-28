package com.mplayeraudio.services.yandex.di

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryRepository
import com.mplayeraudio.services.yandex.YandexMusicRepositoryImpl
import com.mplayeraudio.services.yandex.internal.network.KtorYandexMusicApi
import com.mplayeraudio.services.yandex.internal.network.YandexMusicApi
import org.koin.core.module.Module
import org.koin.dsl.module

fun yandexMusicModule(): Module = module {
    single<YandexMusicApi> {
        KtorYandexMusicApi(
            httpClient = get(),
            json = get(),
        )
    }
    single {
        YandexMusicRepositoryImpl(
            accessTokenProvider = get(),
            api = get(),
        )
    }
    single<MusicLibraryRepository> { get<YandexMusicRepositoryImpl>() }
}
