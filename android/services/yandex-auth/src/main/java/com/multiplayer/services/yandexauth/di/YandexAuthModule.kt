package com.multiplayer.services.yandexauth.di
import com.multiplayer.core.domain.yandexauth.YandexAccessTokenProvider
import com.multiplayer.core.domain.yandexauth.YandexAuthRepository
import com.multiplayer.services.yandexauth.YandexAuthRepositoryImpl
import com.multiplayer.services.yandexauth.YandexOAuthConfig
import com.multiplayer.services.yandexauth.internal.AndroidDeviceMetadataProvider
import com.multiplayer.services.yandexauth.internal.DeviceMetadataProvider
import com.multiplayer.services.yandexauth.internal.PkceGenerator
import com.multiplayer.services.yandexauth.internal.SecurePkceGenerator
import com.multiplayer.services.yandexauth.internal.YandexAuthUrlBuilder
import com.multiplayer.services.yandexauth.internal.YandexAuthorizationCallbackParser
import com.multiplayer.services.yandexauth.internal.YandexTokenRefresher
import com.multiplayer.services.yandexauth.internal.network.KtorYandexOAuthApi
import com.multiplayer.services.yandexauth.internal.network.YandexOAuthApi
import com.multiplayer.services.yandexauth.internal.storage.EncryptedPreferencesFactory
import com.multiplayer.services.yandexauth.internal.storage.EncryptedYandexPendingAuthStore
import com.multiplayer.services.yandexauth.internal.storage.EncryptedYandexSessionStore
import com.multiplayer.services.yandexauth.internal.storage.YandexPendingAuthCodec
import com.multiplayer.services.yandexauth.internal.storage.YandexPendingAuthStore
import com.multiplayer.services.yandexauth.internal.storage.YandexSessionCodec
import com.multiplayer.services.yandexauth.internal.storage.YandexSessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import java.time.Clock
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

@Suppress("LongMethod")
fun yandexAuthModule(config: YandexOAuthConfig): Module = module {
    single { config }
    single<Clock> { Clock.systemUTC() }
    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
    single { EncryptedPreferencesFactory(context = androidContext()) }
    single { YandexSessionCodec(json = get()) }
    single { YandexPendingAuthCodec(json = get()) }
    single<YandexSessionStore> {
        EncryptedYandexSessionStore(
            encryptedPreferencesFactory = get(),
            codec = get(),
        )
    }
    single<YandexPendingAuthStore> {
        EncryptedYandexPendingAuthStore(
            encryptedPreferencesFactory = get(),
            codec = get(),
        )
    }
    single<DeviceMetadataProvider> {
        AndroidDeviceMetadataProvider(
            context = androidContext(),
            config = get(),
        )
    }
    single<PkceGenerator> { SecurePkceGenerator() }
    single { YandexAuthUrlBuilder() }
    single { YandexAuthorizationCallbackParser() }
    single {
        HttpClient(Android)
    }
    single<YandexOAuthApi> {
        KtorYandexOAuthApi(
            httpClient = get(),
            json = get(),
        )
    }
    single {
        YandexTokenRefresher(
            oauthApi = get(),
            config = get(),
            clock = get(),
        )
    }
    single {
        YandexAuthRepositoryImpl(
            config = get(),
            oauthApi = get(),
            sessionStore = get(),
            pendingAuthStore = get(),
            deviceMetadataProvider = get(),
            pkceGenerator = get(),
            authUrlBuilder = get(),
            callbackParser = get(),
            tokenRefresher = get(),
            clock = get(),
        )
    }
    single<YandexAuthRepository> { get<YandexAuthRepositoryImpl>() }
    single<YandexAccessTokenProvider> { get<YandexAuthRepositoryImpl>() }
}
