package com.mplayeraudio.services.yandexauth.internal.network

import com.mplayeraudio.core.domain.yandexauth.YandexAccessToken
import com.mplayeraudio.core.domain.yandexauth.YandexRefreshToken
import com.mplayeraudio.core.domain.yandexauth.YandexUserIdentity
import com.mplayeraudio.services.yandexauth.YandexOAuthConfig
import com.mplayeraudio.services.yandexauth.internal.OAuthTokenPayload

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
