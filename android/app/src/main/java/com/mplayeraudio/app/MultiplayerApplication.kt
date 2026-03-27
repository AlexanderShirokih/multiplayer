package com.mplayeraudio.app

import android.app.Application
import com.mplayeraudio.core.domain.yandexauth.YandexClientId
import com.mplayeraudio.feature.auth.yamusic.yandexMusicAuthModule
import com.mplayeraudio.services.yandexauth.YandexOAuthConfig
import com.mplayeraudio.services.yandexauth.di.yandexAuthModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MultiplayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@MultiplayerApplication)
            modules(
                yandexAuthModule(yandexOAuthConfig()),
                yandexMusicAuthModule(),
            )
        }
    }

    private fun yandexOAuthConfig(): YandexOAuthConfig {
        return YandexOAuthConfig(
            clientId = YandexClientId(BuildConfig.YANDEX_CLIENT_ID),
            clientSecret = BuildConfig.YANDEX_CLIENT_SECRET,
            redirectUri = BuildConfig.YANDEX_REDIRECT_URI,
            deviceName = getString(R.string.app_name),
        )
    }
}
