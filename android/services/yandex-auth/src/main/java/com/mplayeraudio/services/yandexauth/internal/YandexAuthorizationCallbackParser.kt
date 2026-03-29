package com.mplayeraudio.services.yandexauth.internal

import com.mplayeraudio.core.domain.yandexauth.YandexAccessToken
import io.ktor.http.Url
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal class YandexAuthorizationCallbackParser {
    fun parse(callbackUri: String): ParsedAuthorizationCallback {
        val callbackUrl = Url(callbackUri)
        val fragmentParameters = parseParameters(callbackUrl.fragment)
        val queryParameters = callbackUrl.parameters
        return ParsedAuthorizationCallback(
            code = queryParameters["code"] ?: fragmentParameters["code"],
            state = queryParameters["state"] ?: fragmentParameters["state"],
            error = queryParameters["error"] ?: fragmentParameters["error"],
            errorDescription = queryParameters["error_description"] ?: fragmentParameters["error_description"],
            accessToken = (
                queryParameters["access_token"] ?: fragmentParameters["access_token"]
                )?.let(::YandexAccessToken),
            tokenType = queryParameters["token_type"] ?: fragmentParameters["token_type"],
            expiresInSeconds = (
                queryParameters["expires_in"] ?: fragmentParameters["expires_in"]
                )?.toLongOrNull(),
            scopes = parseScopes(
                queryParameters["scope"] ?: fragmentParameters["scope"],
            ),
        )
    }

    private fun parseParameters(rawValue: String): Map<String, String> {
        if (rawValue.isBlank()) {
            return emptyMap()
        }

        return rawValue.split("&")
            .mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                val key = parts.firstOrNull()?.decode() ?: return@mapNotNull null
                if (key.isBlank()) {
                    return@mapNotNull null
                }
                val value = parts.getOrNull(1)?.decode().orEmpty()
                key to value
            }
            .toMap()
    }

    private fun parseScopes(rawScopes: String?): Set<String> {
        return rawScopes.orEmpty()
            .split(" ")
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }

    private fun String.decode(): String {
        return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
    }
}
