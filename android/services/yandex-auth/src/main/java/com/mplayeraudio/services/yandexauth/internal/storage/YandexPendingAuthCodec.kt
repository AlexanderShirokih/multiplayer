package com.mplayeraudio.services.yandexauth.internal.storage

import com.mplayeraudio.core.domain.yandexauth.YandexDeviceId
import com.mplayeraudio.services.yandexauth.internal.PendingYandexAuthorization
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal class YandexPendingAuthCodec(
    private val json: Json,
) {
    fun encode(pendingAuthorization: PendingYandexAuthorization): String {
        return buildJsonObject {
            put("state", pendingAuthorization.state)
            put("codeVerifier", pendingAuthorization.codeVerifier)
            put("deviceId", pendingAuthorization.deviceId.value)
            put("deviceName", pendingAuthorization.deviceName)
            put("requestedAtEpochSeconds", pendingAuthorization.requestedAt.epochSecond)
        }.toString()
    }

    fun decode(rawValue: String?): PendingYandexAuthorization? {
        val encodedValue = rawValue?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val root = json.parseToJsonElement(encodedValue).jsonObject
            PendingYandexAuthorization(
                state = root.requiredString("state"),
                codeVerifier = root.requiredString("codeVerifier"),
                deviceId = YandexDeviceId(root.requiredString("deviceId")),
                deviceName = root.requiredString("deviceName"),
                requestedAt = Instant.ofEpochSecond(root.requiredLong("requestedAtEpochSeconds")),
            )
        }.getOrNull()
    }

    private fun kotlinx.serialization.json.JsonObject.requiredString(key: String): String {
        return this[key]?.jsonPrimitive?.contentOrNull
            ?: error("Missing value for $key")
    }

    private fun kotlinx.serialization.json.JsonObject.requiredLong(key: String): Long {
        return this[key]?.jsonPrimitive?.longOrNull
            ?: error("Missing value for $key")
    }
}
