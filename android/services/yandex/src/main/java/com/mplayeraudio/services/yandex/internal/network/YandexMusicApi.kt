package com.mplayeraudio.services.yandex.internal.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal interface YandexMusicApi {
    suspend fun fetchAvailability(accessToken: String): JsonObject

    suspend fun fetchCurrentUser(accessToken: String): JsonObject

    suspend fun fetchOwnPlaylists(
        accessToken: String,
        userId: String,
    ): JsonArray

    suspend fun fetchPlaylist(
        accessToken: String,
        userId: String,
        kind: Long,
    ): JsonObject

    suspend fun fetchSavedTracks(
        accessToken: String,
        userId: String,
    ): JsonElement

    suspend fun fetchTracks(
        accessToken: String,
        trackIds: List<String>,
    ): JsonArray
}
