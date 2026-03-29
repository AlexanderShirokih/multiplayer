package com.mplayeraudio.services.yandex

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.services.yandex.internal.YandexMusicRequestRunner
import com.mplayeraudio.services.yandex.internal.network.YandexMusicApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

internal class YandexTrackStreamUrlProvider(
    private val requestRunner: YandexMusicRequestRunner,
    private val api: YandexMusicApi,
) : TrackStreamUrlProvider {

    override suspend fun getStreamUrl(trackId: TrackId): String {
        return requestRunner.withAuthorizedRequest { accessToken ->
            resolveStreamUrl(accessToken, trackId.value)
        }
    }

    private suspend fun resolveStreamUrl(accessToken: String, trackId: String): String {
        resolveFullTrackUrl(accessToken, trackId)?.let { fullTrackUrl ->
            return fullTrackUrl
        }

        val downloadInfoArray = api.fetchTrackDownloadInfo(accessToken, trackId)
        if (downloadInfoArray.isEmpty()) {
            throw MusicLibraryException.InvalidResponse(
                description = "No download info available for track $trackId.",
            )
        }

        val bestEntry = downloadInfoArray
            .map { it.jsonObject }
            .selectBestDownloadInfo()

        val downloadInfoUrl = bestEntry.requireString("downloadInfoUrl")
        val streamUrl = if (bestEntry.booleanOrFalse("direct")) {
            downloadInfoUrl
        } else {
            // Legacy download-info responses point to XML metadata that must be converted into
            // a signed /get-mp3/ URL.
            val xmlResponse = api.fetchDownloadInfoUrl(accessToken, downloadInfoUrl)
            buildSignedUrl(xmlResponse)
        }
        return streamUrl
    }

    private suspend fun resolveFullTrackUrl(accessToken: String, trackId: String): String? {
        return try {
            val fileInfo = api.fetchTrackFileInfo(accessToken, trackId)
            val downloadInfo = fileInfo["downloadInfo"]?.jsonObject
            downloadInfo?.firstDownloadUrl()
        } catch (_: MusicLibraryException) {
            null
        }
    }

    private fun JsonObject.requireString(key: String): String {
        return this[key]?.jsonPrimitive?.content
            ?: throw MusicLibraryException.InvalidResponse(
                description = "Missing '$key' in download info response.",
            )
    }
}

private fun List<JsonObject>.selectBestDownloadInfo(): JsonObject {
    val priorities = listOf<(JsonObject) -> Boolean>(
        { entry ->
            !entry.booleanOrFalse("preview") &&
                entry.booleanOrFalse("direct") &&
                entry.stringOrNull("container") == PreferredStreamingContainer &&
                entry.stringOrNull("codec") == PreferredCodec
        },
        { entry ->
            !entry.booleanOrFalse("preview") &&
                entry.booleanOrFalse("direct") &&
                entry.stringOrNull("container") == PreferredStreamingContainer
        },
        { entry ->
            !entry.booleanOrFalse("preview") &&
                entry.booleanOrFalse("direct") &&
                entry.stringOrNull("codec") == PreferredCodec
        },
        { entry ->
            !entry.booleanOrFalse("preview") && entry.booleanOrFalse("direct")
        },
        { entry ->
            !entry.booleanOrFalse("preview") && entry.stringOrNull("codec") == PreferredCodec
        },
        { entry ->
            !entry.booleanOrFalse("preview")
        },
    )

    priorities.forEach { predicate ->
        selectHighestBitrate(predicate)?.let { return it }
    }

    return first()
}

private inline fun List<JsonObject>.selectHighestBitrate(
    predicate: (JsonObject) -> Boolean,
): JsonObject? {
    return filter(predicate)
        .maxByOrNull { entry -> entry.intOrZero("bitrateInKbps") }
}

private fun JsonObject.booleanOrFalse(key: String): Boolean {
    return this[key]?.jsonPrimitive?.boolean ?: false
}

private fun JsonObject.intOrZero(key: String): Int {
    return this[key]?.jsonPrimitive?.int ?: 0
}

private fun JsonObject.stringOrNull(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}

private fun JsonObject.firstDownloadUrl(): String? {
    return stringOrNull("url")
        ?: this["urls"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonPrimitive
            ?.contentOrNull
}

internal fun buildSignedUrl(xmlResponse: String): String {
    val host = xmlResponse.extractXmlTag("host")
    val path = xmlResponse.extractXmlTag("path")
    val ts = xmlResponse.extractXmlTag("ts")
    val s = xmlResponse.extractXmlTag("s")

    val toHash = "$SigningSalt${path.substring(1)}$s"
    val md5 = MessageDigest.getInstance("MD5")
        .digest(toHash.toByteArray())
        .joinToString("") { "%02x".format(it) }

    return "https://$host/get-mp3/$md5/$ts$path"
}

private fun String.extractXmlTag(tag: String): String {
    val openTag = "<$tag>"
    val closeTag = "</$tag>"
    val start = indexOf(openTag)
    if (start < 0) {
        throw MusicLibraryException.InvalidResponse(
            description = "Missing <$tag> in download info XML.",
        )
    }
    val valueStart = start + openTag.length
    val end = indexOf(closeTag, valueStart)
    if (end < 0) {
        throw MusicLibraryException.InvalidResponse(
            description = "Missing </$tag> in download info XML.",
        )
    }
    return substring(valueStart, end)
}

private const val SigningSalt = "XGRlBW9FXlekgbPrRHuSiA"
private const val PreferredCodec = "mp3"
private const val PreferredStreamingContainer = "hls"
