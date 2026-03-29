package com.mplayeraudio.services.yandexauth.internal

import com.mplayeraudio.core.domain.yandexauth.YandexDeviceId
import com.mplayeraudio.services.yandexauth.YandexOAuthConfig
import io.ktor.http.URLBuilder

internal class YandexAuthUrlBuilder {
    fun buildAuthorizationUrl(
        config: YandexOAuthConfig,
        state: String,
        codeChallenge: String,
        deviceId: YandexDeviceId,
        deviceName: String,
    ): String {
        return URLBuilder("https://oauth.yandex.ru/authorize").apply {
            parameters.append("response_type", config.authorizationResponseType.parameterValue)
            parameters.append("client_id", config.authorizationClientId.value)
            parameters.append("redirect_uri", config.authorizationRedirectUri)
            parameters.append("state", state)
            if (config.authorizationResponseType.requiresPkce) {
                parameters.append("device_id", deviceId.value)
                parameters.append("device_name", deviceName)
                parameters.append("code_challenge", codeChallenge)
                parameters.append("code_challenge_method", "S256")
            }
        }.buildString()
    }

    private val com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType.parameterValue: String
        get() = when (this) {
            com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType.Code -> "code"
            com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType.Token -> "token"
        }

    private val com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType.requiresPkce: Boolean
        get() = this == com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType.Code
}
