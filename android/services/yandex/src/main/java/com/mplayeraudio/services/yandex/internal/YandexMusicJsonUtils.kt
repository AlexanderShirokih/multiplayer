package com.mplayeraudio.services.yandex.internal

import com.mplayeraudio.core.domain.musiclibrary.PlaylistVisibility
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun JsonObject.coverUriTemplate(): String? {
    val cover = optionalObject("cover")
    val coverType = cover?.optionalString("type")
    return when (coverType) {
        "pic" -> cover.optionalString("uri") ?: optionalString("ogImage")
        "mosaic" -> cover.optionalArray("itemsUri")
            ?.firstOrNull()
            ?.stringContent()
            ?: optionalString("ogImage")
        else -> optionalString("ogImage")
    }
}

internal fun String.toPlaylistVisibility(): PlaylistVisibility? {
    return when (this) {
        "public" -> PlaylistVisibility.Public
        "private" -> PlaylistVisibility.Private
        else -> null
    }
}

internal fun JsonElement?.stringContent(): String? {
    return (this as? JsonPrimitive)?.contentOrNull
}
