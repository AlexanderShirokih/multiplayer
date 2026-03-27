package com.multiplayer.services.yandexauth.internal

import com.multiplayer.core.domain.yandexauth.YandexAuthException
import com.multiplayer.core.domain.yandexauth.YandexAuthSession
import com.multiplayer.services.yandexauth.YandexOAuthConfig
import com.multiplayer.services.yandexauth.internal.network.YandexOAuthApi
import java.time.Clock
import java.time.Duration

internal class YandexTokenRefresher(
    private val oauthApi: YandexOAuthApi,
    private val config: YandexOAuthConfig,
    private val clock: Clock,
    private val refreshSafetyWindow: Duration = DEFAULT_REFRESH_SAFETY_WINDOW,
) {
    fun shouldRefresh(session: YandexAuthSession): Boolean {
        val expiresAt = session.expiresAt ?: return false
        return expiresAt <= clock.instant().plus(refreshSafetyWindow)
    }

    suspend fun refresh(session: YandexAuthSession): YandexAuthSession {
        val refreshToken = session.refreshToken
            ?: throw YandexAuthException.RefreshFailed(reason = "Yandex refresh token is missing.")

        return try {
            val payload = oauthApi.refreshAccessToken(
                config = config,
                refreshToken = refreshToken,
            )
            session.copy(
                accessToken = payload.accessToken,
                refreshToken = payload.refreshToken ?: refreshToken,
                tokenType = payload.tokenType,
                expiresAt = payload.expiresInSeconds?.let { clock.instant().plusSeconds(it) },
                scopes = if (payload.scopes.isEmpty()) session.scopes else payload.scopes,
            )
        } catch (exception: YandexAuthException) {
            throw YandexAuthException.RefreshFailed(
                reason = exception.message ?: "Unable to refresh Yandex OAuth token.",
                original = exception,
            )
        }
    }

    private companion object {
        val DEFAULT_REFRESH_SAFETY_WINDOW: Duration = Duration.ofMinutes(5)
    }
}
