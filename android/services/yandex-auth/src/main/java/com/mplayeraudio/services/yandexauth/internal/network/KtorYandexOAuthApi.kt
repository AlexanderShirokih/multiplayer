package com.mplayeraudio.services.yandexauth.internal.network

import com.mplayeraudio.core.domain.yandexauth.YandexAccessToken
import com.mplayeraudio.core.domain.yandexauth.YandexAuthException
import com.mplayeraudio.core.domain.yandexauth.YandexRefreshToken
import com.mplayeraudio.core.domain.yandexauth.YandexUserId
import com.mplayeraudio.core.domain.yandexauth.YandexUserIdentity
import com.mplayeraudio.services.yandexauth.YandexOAuthConfig
import com.mplayeraudio.services.yandexauth.internal.OAuthTokenPayload
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.ParametersBuilder
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal class KtorYandexOAuthApi(
    private val httpClient: HttpClient,
    private val json: Json,
) : YandexOAuthApi {

    override suspend fun exchangeAuthorizationCode(
        config: YandexOAuthConfig,
        code: String,
        codeVerifier: String,
        deviceId: String,
        deviceName: String,
    ): OAuthTokenPayload {
        val payload = submitForm("https://oauth.yandex.ru/token") {
            append("grant_type", "authorization_code")
            append("code", code)
            append("client_id", config.clientId.value)
            append("device_id", deviceId)
            append("device_name", deviceName)
            append("code_verifier", codeVerifier)
        }
        return payload.toTokenPayload()
    }

    override suspend fun refreshAccessToken(
        config: YandexOAuthConfig,
        refreshToken: YandexRefreshToken,
    ): OAuthTokenPayload {
        val payload = submitForm("https://oauth.yandex.ru/token") {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken.value)
            append("client_id", config.clientId.value)
            append("client_secret", config.clientSecret)
        }
        return payload.toTokenPayload()
    }

    override suspend fun revokeToken(
        config: YandexOAuthConfig,
        accessToken: YandexAccessToken,
    ) {
        submitForm("https://oauth.yandex.ru/revoke_token") {
            append("access_token", accessToken.value)
            append("client_id", config.clientId.value)
            append("client_secret", config.clientSecret)
        }
    }

    override suspend fun fetchUserIdentity(
        accessToken: YandexAccessToken,
    ): YandexUserIdentity {
        val response = httpClient.get("https://login.yandex.ru/info") {
            parameter("format", "json")
            header(HttpHeaders.Authorization, "OAuth ${accessToken.value}")
        }
        val body = response.bodyAsText()
        val payload = body.asJsonObject()
        if (!response.status.isSuccess()) {
            throw payload.toProviderError()
        }

        val id = payload["id"]?.jsonPrimitive?.contentOrNull
            ?: payload["default_uid"]?.jsonPrimitive?.contentOrNull
            ?: throw YandexAuthException.ProviderError(
                code = "invalid_user_info",
                description = "Yandex user info response does not contain a user id.",
            )

        return YandexUserIdentity(
            id = YandexUserId(id),
            login = payload["login"]?.jsonPrimitive?.contentOrNull,
            displayName = payload["display_name"]?.jsonPrimitive?.contentOrNull,
            email = payload["default_email"]?.jsonPrimitive?.contentOrNull,
            avatarId = payload["default_avatar_id"]?.jsonPrimitive?.contentOrNull
                ?: payload["avatar_id"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private suspend fun submitForm(
        url: String,
        parametersBuilder: ParametersBuilder.() -> Unit,
    ): JsonObject {
        val response = httpClient.submitForm(
            url = url,
            formParameters = Parameters.build(parametersBuilder),
        )
        val body = response.bodyAsText()
        val payload = body.asJsonObject()
        if (!response.status.isSuccess()) {
            throw payload.toProviderError()
        }
        return payload
    }

    private fun JsonObject.toTokenPayload(): OAuthTokenPayload {
        val accessToken = this["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw YandexAuthException.ProviderError(
                code = "invalid_token_payload",
                description = "Yandex OAuth response does not contain access_token.",
            )
        val tokenType = this["token_type"]?.jsonPrimitive?.contentOrNull ?: "bearer"
        val scopes = this["scope"]?.jsonPrimitive?.contentOrNull
            ?.split(" ")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.toSet()
            .orEmpty()
        return OAuthTokenPayload(
            tokenType = tokenType,
            accessToken = YandexAccessToken(accessToken),
            refreshToken = this["refresh_token"]?.jsonPrimitive?.contentOrNull?.let(::YandexRefreshToken),
            expiresInSeconds = this["expires_in"]?.jsonPrimitive?.longOrNull,
            scopes = scopes,
        )
    }

    private fun String.asJsonObject(): JsonObject {
        return json.parseToJsonElement(this).jsonObject
    }

    private fun JsonObject.toProviderError(): YandexAuthException.ProviderError {
        return YandexAuthException.ProviderError(
            code = this["error"]?.jsonPrimitive?.contentOrNull ?: "unknown_error",
            description = this["error_description"]?.jsonPrimitive?.contentOrNull,
        )
    }
}
