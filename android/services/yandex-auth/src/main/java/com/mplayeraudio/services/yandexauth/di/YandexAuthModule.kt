package com.mplayeraudio.services.yandexauth.di
import com.mplayeraudio.core.domain.yandexauth.YandexAccessTokenProvider
import com.mplayeraudio.core.domain.yandexauth.YandexAuthRepository
import com.mplayeraudio.services.yandexauth.YandexAuthRepositoryImpl
import com.mplayeraudio.services.yandexauth.YandexOAuthConfig
import com.mplayeraudio.services.yandexauth.internal.AndroidDeviceMetadataProvider
import com.mplayeraudio.services.yandexauth.internal.DeviceMetadataProvider
import com.mplayeraudio.services.yandexauth.internal.PkceGenerator
import com.mplayeraudio.services.yandexauth.internal.SecurePkceGenerator
import com.mplayeraudio.services.yandexauth.internal.YandexAuthUrlBuilder
import com.mplayeraudio.services.yandexauth.internal.YandexAuthorizationCallbackParser
import com.mplayeraudio.services.yandexauth.internal.YandexTokenRefresher
import com.mplayeraudio.services.yandexauth.internal.network.KtorYandexOAuthApi
import com.mplayeraudio.services.yandexauth.internal.network.YandexOAuthApi
import com.mplayeraudio.services.yandexauth.internal.storage.EncryptedPreferencesFactory
import com.mplayeraudio.services.yandexauth.internal.storage.EncryptedYandexPendingAuthStore
import com.mplayeraudio.services.yandexauth.internal.storage.EncryptedYandexSessionStore
import com.mplayeraudio.services.yandexauth.internal.storage.YandexPendingAuthCodec
import com.mplayeraudio.services.yandexauth.internal.storage.YandexPendingAuthStore
import com.mplayeraudio.services.yandexauth.internal.storage.YandexSessionCodec
import com.mplayeraudio.services.yandexauth.internal.storage.YandexSessionStore
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
