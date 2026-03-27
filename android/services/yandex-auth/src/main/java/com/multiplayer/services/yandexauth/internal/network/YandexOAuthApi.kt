package com.multiplayer.services.yandexauth.internal.network

import com.multiplayer.core.domain.yandexauth.YandexAccessToken
import com.multiplayer.core.domain.yandexauth.YandexRefreshToken
import com.multiplayer.core.domain.yandexauth.YandexUserIdentity
import com.multiplayer.services.yandexauth.YandexOAuthConfig
import com.multiplayer.services.yandexauth.internal.OAuthTokenPayload

internal interface YandexOAuthApi {
    suspend fun exchangeAuthorizationCode(
        config: YandexOAuthConfig,
        code: String,
        codeVerifier: String,
        deviceId: String,
        deviceName: String,
    ): OAuthTokenPayload

    suspend fun refreshAccessToken(
        config: YandexOAuthConfig,
        refreshToken: YandexRefreshToken,
    ): OAuthTokenPayload

    suspend fun revokeToken(
        config: YandexOAuthConfig,
        accessToken: YandexAccessToken,
    )

    suspend fun fetchUserIdentity(
        accessToken: YandexAccessToken,
    ): YandexUserIdentity
}
