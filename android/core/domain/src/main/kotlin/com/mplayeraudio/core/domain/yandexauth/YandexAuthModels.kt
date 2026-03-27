package com.mplayeraudio.core.domain.yandexauth

import java.time.Instant

@JvmInline
value class YandexAccessToken(val value: String)

@JvmInline
value class YandexRefreshToken(val value: String)

@JvmInline
value class YandexClientId(val value: String)

@JvmInline
value class YandexUserId(val value: String)

@JvmInline
value class YandexDeviceId(val value: String)

data class YandexAuthorizationRequest(
    val url: String,
)

data class YandexUserIdentity(
    val id: YandexUserId,
    val login: String?,
    val displayName: String?,
    val email: String?,
    val avatarId: String?,
)

data class YandexAuthSession(
    val accessToken: YandexAccessToken,
    val refreshToken: YandexRefreshToken?,
    val tokenType: String,
    val expiresAt: Instant?,
    val scopes: Set<String>,
    val deviceId: YandexDeviceId,
    val user: YandexUserIdentity,
    val clientId: YandexClientId,
)

sealed interface YandexAuthStatus {
    data object Unauthorized : YandexAuthStatus

    data object Authorizing : YandexAuthStatus

    data class Authorized(
        val session: YandexAuthSession,
    ) : YandexAuthStatus

    data class Failed(
        val error: YandexAuthError,
    ) : YandexAuthStatus
}

sealed interface YandexAuthError

sealed class YandexAuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause), YandexAuthError {

    data object MissingConfiguration : YandexAuthException(
        message = "Yandex OAuth configuration is missing.",
    )

    data object MissingSession : YandexAuthException(
        message = "Yandex session is missing.",
    )

    data object MissingPendingAuthorization : YandexAuthException(
        message = "Pending Yandex authorization request is missing.",
    )

    data object MissingAuthorizationCode : YandexAuthException(
        message = "Yandex OAuth callback does not contain an authorization code.",
    )

    data object InvalidCallbackState : YandexAuthException(
        message = "Yandex OAuth callback state is invalid.",
    )

    data class AccessDenied(
        val description: String?,
    ) : YandexAuthException(
        message = description ?: "Yandex OAuth access was denied.",
    )

    data class ProviderError(
        val code: String,
        val description: String?,
    ) : YandexAuthException(
        message = description ?: code,
    )

    data class NetworkFailure(
        val reason: String,
        val original: Throwable? = null,
    ) : YandexAuthException(
        message = reason,
        cause = original,
    )

    data class RefreshFailed(
        val reason: String,
        val original: Throwable? = null,
    ) : YandexAuthException(
        message = reason,
        cause = original,
    )
}
