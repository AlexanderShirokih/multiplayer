package com.mplayeraudio.services.yandexauth

import com.mplayeraudio.core.domain.yandexauth.YandexAuthException
import com.mplayeraudio.core.domain.yandexauth.YandexClientId

data class YandexOAuthConfig(
    val clientId: YandexClientId,
    val clientSecret: String,
    val redirectUri: String,
    val deviceName: String? = null,
) {
    fun requireAuthorizationConfig() {
        if (clientId.value.isBlank() || redirectUri.isBlank()) {
            throw YandexAuthException.MissingConfiguration
        }
    }

    fun requireRefreshConfig() {
        requireAuthorizationConfig()
        if (clientSecret.isBlank()) {
            throw YandexAuthException.MissingConfiguration
        }
    }
}
