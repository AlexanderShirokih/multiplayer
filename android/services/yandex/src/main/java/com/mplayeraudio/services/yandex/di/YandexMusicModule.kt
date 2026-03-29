package com.mplayeraudio.services.yandex.di

import com.mplayeraudio.services.yandex.BuildConfig
import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryRepository
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.services.yandex.YandexMusicRepositoryImpl
import com.mplayeraudio.services.yandex.YandexTrackStreamUrlProvider
import com.mplayeraudio.services.yandex.internal.YandexMusicRequestRunner
import com.mplayeraudio.services.yandex.internal.network.KtorYandexMusicApi
import com.mplayeraudio.services.yandex.internal.network.YandexMusicApi
import com.mplayeraudio.services.yandex.internal.network.YandexMusicStreamingConfig
import org.koin.core.module.Module
import org.koin.dsl.module

fun yandexMusicModule(): Module = module {
    single {
        YandexMusicStreamingConfig(
            downloadInfoSigningSecret = BuildConfig.YANDEX_MUSIC_STREAMING_SECRET,
            downloadInfoClientHeader = BuildConfig.YANDEX_MUSIC_STREAMING_CLIENT,
            fileInfoSigningSecret = BuildConfig.YANDEX_MUSIC_FILE_INFO_SECRET,
            fileInfoClientHeader = BuildConfig.YANDEX_MUSIC_FILE_INFO_CLIENT,
        )
    }
    single<YandexMusicApi> {
        KtorYandexMusicApi(
            httpClient = get(),
            json = get(),
            streamingConfig = get(),
        )
    }
    single {
        YandexMusicRequestRunner(
            accessTokenProvider = get(),
            api = get(),
        )
    }
    single {
        YandexMusicRepositoryImpl(
            requestRunner = get(),
            api = get(),
        )
    }
    single<MusicLibraryRepository> { get<YandexMusicRepositoryImpl>() }
    single<TrackStreamUrlProvider> {
        YandexTrackStreamUrlProvider(
            requestRunner = get(),
            api = get(),
        )
    }
}
