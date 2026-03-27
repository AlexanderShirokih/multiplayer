package com.multiplayer.services.yandexauth.internal

import com.multiplayer.core.domain.yandexauth.YandexAccessToken
import com.multiplayer.core.domain.yandexauth.YandexDeviceId
import com.multiplayer.core.domain.yandexauth.YandexRefreshToken
import java.time.Instant

internal data class PendingYandexAuthorization(
    val state: String,
    val codeVerifier: String,
    val deviceId: YandexDeviceId,
    val deviceName: String,
    val requestedAt: Instant,
)

internal data class OAuthTokenPayload(
    val tokenType: String,
    val accessToken: YandexAccessToken,
    val refreshToken: YandexRefreshToken?,
    val expiresInSeconds: Long?,
    val scopes: Set<String>,
)

internal data class ParsedAuthorizationCallback(
    val code: String?,
    val state: String?,
    val error: String?,
    val errorDescription: String?,
)

internal data class PkcePayload(
    val verifier: String,
    val challenge: String,
    val state: String,
)
