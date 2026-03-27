package com.multiplayer.services.yandexauth.internal.storage

import com.multiplayer.core.domain.yandexauth.YandexAccessToken
import com.multiplayer.core.domain.yandexauth.YandexAuthSession
import com.multiplayer.core.domain.yandexauth.YandexClientId
import com.multiplayer.core.domain.yandexauth.YandexDeviceId
import com.multiplayer.core.domain.yandexauth.YandexRefreshToken
import com.multiplayer.core.domain.yandexauth.YandexUserId
import com.multiplayer.core.domain.yandexauth.YandexUserIdentity
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal class YandexSessionCodec(
    private val json: Json,
) {
    fun encode(session: YandexAuthSession): String {
        return buildJsonObject {
            put("accessToken", session.accessToken.value)
            put("refreshToken", session.refreshToken?.value)
            put("tokenType", session.tokenType)
            put("expiresAtEpochSeconds", session.expiresAt?.epochSecond)
            putJsonArray("scopes") {
                session.scopes.sorted().forEach { scope ->
                    add(JsonPrimitive(scope))
                }
            }
            put("deviceId", session.deviceId.value)
            put("clientId", session.clientId.value)
            put("user", buildJsonObject {
                put("id", session.user.id.value)
                put("login", session.user.login)
                put("displayName", session.user.displayName)
                put("email", session.user.email)
                put("avatarId", session.user.avatarId)
            })
        }.toString()
    }

    fun decode(rawValue: String?): YandexAuthSession? {
        val encodedValue = rawValue?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val root = json.parseToJsonElement(encodedValue).jsonObject
            val user = root.requiredObject("user")
            val scopes = root["scopes"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.toSet()
                .orEmpty()

            YandexAuthSession(
                accessToken = YandexAccessToken(root.requiredString("accessToken")),
                refreshToken = root.string("refreshToken")?.let(::YandexRefreshToken),
                tokenType = root.requiredString("tokenType"),
                expiresAt = root.long("expiresAtEpochSeconds")?.let(Instant::ofEpochSecond),
                scopes = scopes,
                deviceId = YandexDeviceId(root.requiredString("deviceId")),
                user = YandexUserIdentity(
                    id = YandexUserId(user.requiredString("id")),
                    login = user.string("login"),
                    displayName = user.string("displayName"),
                    email = user.string("email"),
                    avatarId = user.string("avatarId"),
                ),
                clientId = YandexClientId(root.requiredString("clientId")),
            )
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

    private fun JsonObject.requiredString(key: String): String {
        return string(key) ?: error("Missing value for $key")
    }

    private fun JsonObject.requiredObject(key: String): JsonObject {
        return this[key]?.jsonObject ?: error("Missing object for $key")
    }
}
