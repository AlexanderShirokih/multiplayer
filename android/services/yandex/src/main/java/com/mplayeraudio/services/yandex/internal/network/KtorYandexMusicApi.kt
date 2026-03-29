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
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Suppress("TooManyFunctions")
internal class KtorYandexMusicApi(
    private val httpClient: HttpClient,
    private val json: Json,
    private val streamingConfig: YandexMusicStreamingConfig,
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

    override suspend fun fetchTrackDownloadInfo(
        accessToken: String,
        trackId: String,
    ): JsonArray {
        val streamingRequest = streamingConfig
            .takeIf(YandexMusicStreamingConfig::isDownloadInfoEnabled)
            ?.let { config ->
                buildStreamingDownloadInfoRequest(
                    trackId = trackId,
                    timestampSeconds = System.currentTimeMillis() / MillisecondsPerSecond,
                    signingSecret = config.downloadInfoSigningSecret,
                    clientHeader = config.downloadInfoClientHeader,
                )
            }
        return getWrappedResult(
            url = yandexMusicUrl("tracks/$trackId/download-info"),
            accessToken = accessToken,
            requestParameters = streamingRequest?.parameters.orEmpty(),
            extraHeaders = streamingRequest?.headers.orEmpty(),
        ).jsonArray
    }

    override suspend fun fetchTrackFileInfo(
        accessToken: String,
        trackId: String,
    ): JsonObject {
        val fileInfoRequest = requireTrackFileInfoRequest(trackId)

        val response = httpClient.get(yandexMusicUrl("get-file-info")) {
            header(HttpHeaders.Authorization, "OAuth $accessToken")
            fileInfoRequest.parameters.forEach { (key, value) ->
                parameter(key, value)
            }
            fileInfoRequest.headers.forEach { (key, value) ->
                header(key, value)
            }
        }
        val payload = response.bodyAsText().asJsonObject()
        if (!response.status.isSuccess()) {
            throw payload.toProviderError(statusCode = response.status.value)
        }
        return payload.requireResultObject("get-file-info")
    }

    override suspend fun fetchDownloadInfoUrl(
        accessToken: String,
        url: String,
    ): String {
        val response = httpClient.get(url) {
            header(HttpHeaders.Authorization, "OAuth $accessToken")
        }
        if (!response.status.isSuccess()) {
            throw MusicLibraryException.ProviderError(
                code = "http_${response.status.value}",
                description = "Failed to fetch download info from $url",
            )
        }
        return response.bodyAsText()
    }

    private suspend fun getWrappedResult(
        url: String,
        accessToken: String,
        requestParameters: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
    ): JsonElement {
        val response = httpClient.get(url) {
            header(HttpHeaders.Authorization, "OAuth $accessToken")
            requestParameters.forEach { (key, value) ->
                parameter(key, value)
            }
            extraHeaders.forEach { (key, value) ->
                header(key, value)
            }
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

    private fun requireTrackFileInfoRequest(trackId: String): SignedDownloadInfoRequest {
        val config = streamingConfig.takeIf(YandexMusicStreamingConfig::isFileInfoEnabled)
            ?: throw MusicLibraryException.InvalidResponse(
                description = "Yandex full track file-info configuration is missing.",
            )
        return buildTrackFileInfoRequest(
            trackId = trackId,
            timestampSeconds = System.currentTimeMillis() / MillisecondsPerSecond,
            signingSecret = config.fileInfoSigningSecret,
            clientHeader = config.fileInfoClientHeader,
        )
    }

    private fun JsonObject.requireResultObject(endpointName: String): JsonObject {
        return this["result"]?.jsonObject
            ?: throw MusicLibraryException.InvalidResponse(
                description = "Yandex Music $endpointName response does not contain result.",
            )
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

internal fun buildStreamingDownloadInfoRequest(
    trackId: String,
    timestampSeconds: Long,
    signingSecret: String,
    clientHeader: String,
): SignedDownloadInfoRequest {
    val signaturePayload = "$trackId$timestampSeconds"
    val mac = Mac.getInstance(HmacSha256Algorithm)
    mac.init(SecretKeySpec(signingSecret.toByteArray(Charsets.UTF_8), HmacSha256Algorithm))
    val sign = Base64.getEncoder()
        .encodeToString(mac.doFinal(signaturePayload.toByteArray(Charsets.UTF_8)))

    return SignedDownloadInfoRequest(
        parameters = mapOf(
            "can_use_streaming" to "true",
            "ts" to timestampSeconds.toString(),
            "sign" to sign,
        ),
        headers = mapOf(YandexMusicClientHeaderName to clientHeader),
    )
}

internal fun buildTrackFileInfoRequest(
    trackId: String,
    timestampSeconds: Long,
    signingSecret: String,
    clientHeader: String,
): SignedDownloadInfoRequest {
    val quality = FileInfoQuality
    val codecs = FileInfoCodecs
    val transport = FileInfoTransport
    val signaturePayload = "$timestampSeconds$trackId$quality$codecs$transport"
        .replace(",", "")
    val mac = Mac.getInstance(HmacSha256Algorithm)
    mac.init(SecretKeySpec(signingSecret.toByteArray(Charsets.UTF_8), HmacSha256Algorithm))
    val sign = Base64.getEncoder()
        .encodeToString(mac.doFinal(signaturePayload.toByteArray(Charsets.UTF_8)))
        .replace("=", "")

    return SignedDownloadInfoRequest(
        parameters = mapOf(
            "ts" to timestampSeconds.toString(),
            "trackId" to trackId,
            "quality" to quality,
            "codecs" to codecs,
            "transports" to transport,
            "sign" to sign,
        ),
        headers = mapOf(YandexMusicClientHeaderName to clientHeader),
    )
}

internal data class SignedDownloadInfoRequest(
    val parameters: Map<String, String>,
    val headers: Map<String, String>,
)

private const val YandexMusicApiBaseUrl = "https://api.music.yandex.net"
private const val HttpStatusUnauthorized = 401
private const val HttpStatusUnavailableForLegalReasons = 451
private const val MillisecondsPerSecond = 1_000L
private const val HmacSha256Algorithm = "HmacSHA256"
private const val YandexMusicClientHeaderName = "X-Yandex-Music-Client"
private const val FileInfoQuality = "lossless"
private const val FileInfoCodecs = "mp3"
private const val FileInfoTransport = "raw"
