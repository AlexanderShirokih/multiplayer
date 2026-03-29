package com.mplayeraudio.services.yandex

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.services.yandex.internal.YandexMusicRequestRunner
import com.mplayeraudio.services.yandex.internal.network.YandexMusicApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
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
        val downloadInfoArray = api.fetchTrackDownloadInfo(accessToken, trackId)
        if (downloadInfoArray.isEmpty()) {
            throw MusicLibraryException.InvalidResponse(
                description = "No download info available for track $trackId.",
            )
        }

        val bestEntry = downloadInfoArray
            .map { it.jsonObject }
            .filter { !it.booleanOrFalse("preview") }
            .maxByOrNull { it.intOrZero("bitrateInKbps") }
            ?: downloadInfoArray.first().jsonObject

        val isDirect = bestEntry.booleanOrFalse("direct")
        if (isDirect) {
            return bestEntry.requireString("downloadInfoUrl")
        }

        val downloadInfoUrl = bestEntry.requireString("downloadInfoUrl")
        val xmlResponse = api.fetchDownloadInfoUrl(accessToken, downloadInfoUrl)
        return buildSignedUrl(xmlResponse)
    }

    private fun JsonObject.booleanOrFalse(key: String): Boolean {
        return this[key]?.jsonPrimitive?.boolean ?: false
    }

    private fun JsonObject.intOrZero(key: String): Int {
        return this[key]?.jsonPrimitive?.int ?: 0
    }

    private fun JsonObject.requireString(key: String): String {
        return this[key]?.jsonPrimitive?.content
            ?: throw MusicLibraryException.InvalidResponse(
                description = "Missing '$key' in download info response.",
            )
    }
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
