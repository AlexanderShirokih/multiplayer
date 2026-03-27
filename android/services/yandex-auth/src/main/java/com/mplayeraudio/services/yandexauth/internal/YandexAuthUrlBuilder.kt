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
            parameters.append("response_type", "code")
            parameters.append("client_id", config.clientId.value)
            parameters.append("redirect_uri", config.redirectUri)
            parameters.append("device_id", deviceId.value)
            parameters.append("device_name", deviceName)
            parameters.append("state", state)
            parameters.append("code_challenge", codeChallenge)
            parameters.append("code_challenge_method", "S256")
        }.buildString()
    }
}
