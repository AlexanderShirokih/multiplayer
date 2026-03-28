package com.mplayeraudio.services.yandex.internal

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.requiredObject(key: String): JsonObject {
    return optionalObject(key)
        ?: throw MusicLibraryException.InvalidResponse(
            description = "Yandex Music response does not contain object '$key'.",
        )
}

internal fun JsonObject.optionalObject(key: String): JsonObject? {
    return this[key]?.takeUnless { it is JsonNull }?.jsonObject
}

internal fun JsonObject.optionalArray(key: String): JsonArray? {
    return this[key]?.takeUnless { it is JsonNull }?.jsonArray
}

internal fun JsonObject.requiredString(key: String): String {
    return optionalString(key)
        ?: throw MusicLibraryException.InvalidResponse(
            description = "Yandex Music response does not contain string '$key'.",
        )
}

internal fun JsonObject.optionalString(key: String): String? {
    return this[key].stringContent()
}

internal fun JsonObject.requiredStringOrLong(key: String): String {
    return optionalStringOrLong(key)
        ?: throw MusicLibraryException.InvalidResponse(
            description = "Yandex Music response does not contain identifier '$key'.",
        )
}

internal fun JsonObject.optionalStringOrLong(key: String): String? {
    return optionalString(key) ?: optionalLong(key)?.toString()
}

internal fun JsonObject.requiredLong(key: String): Long {
    return optionalLong(key)
        ?: throw MusicLibraryException.InvalidResponse(
            description = "Yandex Music response does not contain number '$key'.",
        )
}

internal fun JsonObject.optionalLong(key: String): Long? {
    return (this[key] as? JsonPrimitive)?.longOrNull
}

internal fun JsonObject.requiredBoolean(key: String): Boolean {
    return optionalBoolean(key)
        ?: throw MusicLibraryException.InvalidResponse(
            description = "Yandex Music response does not contain boolean '$key'.",
        )
}

internal fun JsonObject.optionalBoolean(key: String): Boolean? {
    return (this[key] as? JsonPrimitive)?.booleanOrNull
}
