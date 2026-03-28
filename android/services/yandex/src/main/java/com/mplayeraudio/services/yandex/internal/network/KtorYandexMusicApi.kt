package com.mplayeraudio.services.yandex.internal.network

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal class KtorYandexMusicApi(
    private val httpClient: HttpClient,
    private val json: Json,
) : YandexMusicApi {

    override suspend fun fetchAvailability(accessToken: String): JsonObject {
        return getWrappedResult(yandexMusicUrl("account/status"), accessToken).jsonObject
    }

    override suspend fun fetchCurrentUser(accessToken: String): JsonObject {
        val response = httpClient.get("https://login.yandex.ru/info") {
            parameter("format", "json")
            header(HttpHeaders.Authorization, "OAuth $accessToken")
        }
        val payload = response.bodyAsText().asJsonObject()
        if (!response.status.isSuccess()) {
            throw payload.toProviderError(statusCode = response.status.value)
        }
        return payload
    }

    override suspend fun fetchOwnPlaylists(
        accessToken: String,
        userId: String,
    ): JsonArray {
        return getWrappedResult(
            url = yandexMusicUrl("users/$userId/playlists/list"),
            accessToken = accessToken,
        ).jsonArray
    }

    override suspend fun fetchPlaylist(
        accessToken: String,
        userId: String,
        kind: Long,
    ): JsonObject {
        return getWrappedResult(
            url = yandexMusicUrl("users/$userId/playlists/$kind"),
            accessToken = accessToken,
        ).jsonObject
    }

    override suspend fun fetchSavedTracks(
        accessToken: String,
        userId: String,
    ): JsonElement {
        return getWrappedResult(
            url = yandexMusicUrl("users/$userId/likes/tracks"),
            accessToken = accessToken,
        )
    }

    override suspend fun fetchTracks(
        accessToken: String,
        trackIds: List<String>,
    ): JsonArray {
        return submitWrappedForm(
            url = yandexMusicUrl("tracks/"),
            accessToken = accessToken,
        ) {
            trackIds.forEach { append("track-ids", it) }
            append("with-positions", "true")
        }.jsonArray
    }

    private suspend fun getWrappedResult(
        url: String,
        accessToken: String,
    ): JsonElement {
        val response = httpClient.get(url) {
            header(HttpHeaders.Authorization, "OAuth $accessToken")
        }
        val payload = response.bodyAsText().asJsonObject()
        if (!response.status.isSuccess()) {
            throw payload.toProviderError(statusCode = response.status.value)
        }
        return payload["result"]
            ?: throw MusicLibraryException.InvalidResponse(
                description = "Yandex Music response does not contain result.",
            )
    }

    private suspend fun submitWrappedForm(
        url: String,
        accessToken: String,
        parametersBuilder: ParametersBuilder.() -> Unit,
    ): JsonElement {
        val response = httpClient.submitForm(
            url = url,
            formParameters = Parameters.build(parametersBuilder),
        ) {
            header(HttpHeaders.Authorization, "OAuth $accessToken")
        }
        val payload = response.bodyAsText().asJsonObject()
        if (!response.status.isSuccess()) {
            throw payload.toProviderError(statusCode = response.status.value)
        }
        return payload["result"]
            ?: throw MusicLibraryException.InvalidResponse(
                description = "Yandex Music response does not contain result.",
            )
    }

    private fun String.asJsonObject(): JsonObject {
        return json.parseToJsonElement(this).jsonObject
    }

    private fun JsonObject.toProviderError(statusCode: Int): MusicLibraryException {
        val error = this["error"]
        val errorObject = error as? JsonObject
        val errorName = errorObject?.get("name")?.asContent()
        val errorMessage = errorObject?.get("message")?.asContent()
            ?: (error as? JsonPrimitive)?.contentOrNull

        return when (statusCode) {
            HttpStatusUnauthorized -> MusicLibraryException.Unauthorized
            HttpStatusUnavailableForLegalReasons -> MusicLibraryException.ServiceUnavailable(
                description = errorMessage ?: errorName,
            )
            else -> MusicLibraryException.ProviderError(
                code = errorName ?: "http_$statusCode",
                description = errorMessage,
            )
        }
    }
}

private fun yandexMusicUrl(path: String): String = "$YandexMusicApiBaseUrl/$path"

private fun JsonElement.asContent(): String? {
    return (this as? JsonPrimitive)?.contentOrNull
}

private const val YandexMusicApiBaseUrl = "https://api.music.yandex.net"
private const val HttpStatusUnauthorized = 401
private const val HttpStatusUnavailableForLegalReasons = 451
