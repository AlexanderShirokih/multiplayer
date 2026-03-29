package com.mplayeraudio.services.yandexauth

import com.mplayeraudio.core.domain.yandexauth.YandexAuthException
import com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType
import com.mplayeraudio.core.domain.yandexauth.YandexClientId

data class YandexOAuthConfig(
    val clientId: YandexClientId,
    val clientSecret: String,
    val redirectUri: String,
    val deviceName: String? = null,
    val authorizationClientId: YandexClientId = clientId,
    val authorizationRedirectUri: String = redirectUri,
    val authorizationResponseType: YandexAuthorizationResponseType = YandexAuthorizationResponseType.Code,
) {
    fun requireAuthorizationConfig() {
        if (authorizationClientId.value.isBlank() || authorizationRedirectUri.isBlank()) {
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
