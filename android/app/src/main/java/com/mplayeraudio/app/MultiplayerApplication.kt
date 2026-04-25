package com.mplayeraudio.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.kithara.Kithara
import com.kithara.LogLevel
import com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType
import com.mplayeraudio.core.domain.yandexauth.YandexClientId
import com.mplayeraudio.feature.auth.yamusic.yandexMusicAuthModule
import com.mplayeraudio.feature.library.musicLibraryModule
import com.mplayeraudio.services.devicemusic.di.deviceMusicModule
import com.mplayeraudio.services.kithara.di.kitharaModule
import com.mplayeraudio.services.mediasession.MediaPlaybackNotificationChannelId
import com.mplayeraudio.services.mediasession.di.mediaSessionModule
import com.mplayeraudio.services.userplaylists.di.userPlaylistsModule
import com.mplayeraudio.services.yandex.di.yandexMusicModule
import com.mplayeraudio.services.yandexauth.YandexOAuthConfig
import com.mplayeraudio.services.yandexauth.di.yandexAuthModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MultiplayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Kithara.initialize(this, LogLevel.Debug)
        createMediaPlaybackNotificationChannel()

        startKoin {
            androidContext(this@MultiplayerApplication)
            modules(
                appModule(),
                yandexAuthModule(yandexOAuthConfig()),
                deviceMusicModule(),
                yandexMusicModule(),
                userPlaylistsModule,
                kitharaModule(),
                mediaSessionModule(),
                yandexMusicAuthModule(),
                musicLibraryModule(),
            )
        }
    }

    private fun yandexOAuthConfig(): YandexOAuthConfig {
        return YandexOAuthConfig(
            clientId = YandexClientId(BuildConfig.YANDEX_CLIENT_ID),
            clientSecret = BuildConfig.YANDEX_CLIENT_SECRET,
            redirectUri = BuildConfig.YANDEX_REDIRECT_URI,
            deviceName = getString(R.string.app_name),
            authorizationClientId = YandexClientId(BuildConfig.YANDEX_AUTH_CLIENT_ID),
            authorizationRedirectUri = BuildConfig.YANDEX_AUTH_REDIRECT_URI,
            authorizationResponseType = yandexAuthorizationResponseType(),
        )
    }

    private fun yandexAuthorizationResponseType(): YandexAuthorizationResponseType {
        return when (BuildConfig.YANDEX_AUTH_RESPONSE_TYPE.lowercase()) {
            "token" -> YandexAuthorizationResponseType.Token
            else -> YandexAuthorizationResponseType.Code
        }
    }

    private fun createMediaPlaybackNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            MediaPlaybackNotificationChannelId,
            getString(R.string.media_playback_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }
}
