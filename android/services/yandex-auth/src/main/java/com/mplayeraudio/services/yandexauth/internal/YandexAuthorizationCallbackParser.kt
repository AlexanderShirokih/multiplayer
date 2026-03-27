package com.mplayeraudio.services.yandexauth.internal

import io.ktor.http.Url

internal class YandexAuthorizationCallbackParser {
    fun parse(callbackUri: String): ParsedAuthorizationCallback {
        val callbackUrl = Url(callbackUri)
        return ParsedAuthorizationCallback(
            code = callbackUrl.parameters["code"],
            state = callbackUrl.parameters["state"],
            error = callbackUrl.parameters["error"],
            errorDescription = callbackUrl.parameters["error_description"],
        )
    }
}
