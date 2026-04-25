package com.mplayeraudio.services.yandex

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistVisibility
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.core.domain.yandexauth.YandexAccessTokenProvider
import com.mplayeraudio.services.yandex.internal.YandexMusicRequestRunner
import com.mplayeraudio.services.yandex.internal.network.YandexMusicApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexMusicProviderTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `observePlaylists emits refreshed summary response`() = runTest {
        val repository = repository(
            api = FakeYandexMusicApi(
                currentUser = jsonObject(
                    """
                    {
                      "id": "264684056"
                    }
                    """.trimIndent(),
                ),
                ownPlaylists = jsonArray(
                    """
                    [
                      {
                        "owner": {
                          "uid": 264684056,
                          "name": "Александр Широких"
                        },
                        "playlistUuid": "6852c997-215a-6b20-8574-762e75b7e593",
                        "available": true,
                        "uid": 264684056,
                        "kind": 1008,
                        "title": "shared",
                        "revision": 13,
                        "snapshot": 13,
                        "trackCount": 12,
                        "visibility": "public",
                        "collective": false,
                        "created": "2024-07-16T09:13:32+00:00",
                        "modified": "2024-07-18T10:16:52+00:00",
                        "isBanner": false,
                        "isPremiere": false,
                        "durationMs": 2757910,
                        "cover": {
                          "type": "mosaic",
                          "itemsUri": [
                            "avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%"
                          ],
                          "custom": false
                        },
                        "ogImage": "avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%",
                        "tags": []
                      }
                    ]
                    """.trimIndent(),
                ),
            ),
        )

        repository.refreshPlaylists()
        val playlists = repository.observePlaylists().first()

        assertEquals(1, playlists.size)
        val summary = playlists.first()
        assertEquals(MusicProviderId.YandexMusic, summary.provider)
        assertEquals("264684056", summary.id.ownerId.value)
        assertEquals(1008L, summary.id.kind.value)
        assertEquals("shared", summary.title)
        assertEquals("Александр Широких", summary.ownerName)
        assertEquals("avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%", summary.coverUriTemplate)
        assertEquals(12, summary.trackCount)
        assertEquals(2757910L, summary.durationMs)
        assertEquals(PlaylistVisibility.Public, summary.visibility)
    }

    @Test
    fun `observePlaylist emits refreshed rich track entries`() = runTest {
        val repository = repository(
            api = FakeYandexMusicApi(
                playlist = jsonObject(
                    """
                    {
                      "owner": {
                        "uid": 264684056,
                        "name": "Александр Широких"
                      },
                      "playlistUuid": "6852c997-215a-6b20-8574-762e75b7e593",
                      "available": true,
                      "uid": 264684056,
                      "kind": 1008,
                      "title": "shared",
                      "revision": 13,
                      "snapshot": 13,
                      "trackCount": 12,
                      "visibility": "public",
                      "collective": false,
                      "durationMs": 2757910,
                      "likesCount": 2,
                      "cover": {
                        "type": "mosaic",
                        "itemsUri": [
                          "avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%"
                        ],
                        "custom": false
                      },
                      "tracks": [
                        {
                          "id": 33207297,
                          "originalIndex": 0,
                          "originalShuffleIndex": 11,
                          "recent": false,
                          "timestamp": "2024-07-18T10:16:52+00:00",
                          "track": {
                            "id": "33207297",
                            "title": "Track title",
                            "available": true,
                            "availableForPremiumUsers": true,
                            "availableFullWithoutPermission": false,
                            "coverUri": "avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%",
                            "durationMs": 222000,
                            "lyricsAvailable": true,
                            "artists": [
                              {
                                "id": 15,
                                "name": "Artist"
                              }
                            ],
                            "albums": [
                              {
                                "id": 4056452
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val playlistId = PlaylistId(
            ownerId = ProviderUserId("264684056"),
            kind = PlaylistKind(1008),
        )
        repository.refreshPlaylist(playlistId)
        val playlist = requireNotNull(repository.observePlaylist(playlistId).first())

        assertEquals(13L, playlist.revision)
        assertEquals(13L, playlist.snapshot)
        assertEquals(2, playlist.likesCount)
        assertEquals(1, playlist.tracks.size)

        val entry = playlist.tracks.first()
        assertEquals(0, entry.position)
        assertEquals(0, entry.originalIndex)
        assertEquals(11, entry.originalShuffleIndex)
        assertEquals(YandexTrackId("33207297"), entry.trackRef.trackId)
        assertEquals("4056452", entry.trackRef.albumId?.value)
        assertEquals("Track title", entry.track?.preview?.title)
        assertTrue(entry.track?.lyricsAvailable == true)
    }

    @Test
    fun `observeSavedTracks emits private library sentinel after refresh`() = runTest {
        val repository = repository(
            api = FakeYandexMusicApi(
                currentUser = jsonObject("""{"id":"264684056"}"""),
                savedTracks = json.parseToJsonElement("\"private-library\""),
            ),
        )

        repository.refreshSavedTracks()
        val result = repository.observeSavedTracks().first()

        assertTrue(result is SavedTracksResult.PrivateLibrary)
    }

    private fun repository(
        api: YandexMusicApi,
    ): YandexMusicProvider {
        return YandexMusicProvider(
            requestRunner = YandexMusicRequestRunner(
                accessTokenProvider = object : YandexAccessTokenProvider {
                    override fun accessTokenFlow(): Flow<String?> = emptyFlow()

                    override suspend fun getValidAccessToken(forceRefresh: Boolean): String = "token"
                },
                api = api,
            ),
            api = api,
        )
    }

    private fun jsonObject(value: String): JsonObject = json.parseToJsonElement(value).jsonObject

    private fun jsonArray(value: String): JsonArray = json.parseToJsonElement(value).jsonArray
}

private class FakeYandexMusicApi(
    private val availability: JsonObject = JsonObject(emptyMap()),
    private val currentUser: JsonObject = JsonObject(emptyMap()),
    private val ownPlaylists: JsonArray = JsonArray(emptyList()),
    private val playlist: JsonObject = JsonObject(emptyMap()),
    private val savedTracks: JsonElement = JsonObject(emptyMap()),
    private val tracks: JsonArray = JsonArray(emptyList()),
    private val trackDownloadInfo: JsonArray = JsonArray(emptyList()),
    private val downloadInfoUrlResponse: String = "",
) : YandexMusicApi {

    override suspend fun fetchAvailability(accessToken: String): JsonObject = availability

    override suspend fun fetchCurrentUser(accessToken: String): JsonObject = currentUser

    override suspend fun fetchOwnPlaylists(accessToken: String, userId: String): JsonArray = ownPlaylists

    override suspend fun fetchPlaylist(accessToken: String, userId: String, kind: Long): JsonObject = playlist

    override suspend fun fetchSavedTracks(accessToken: String, userId: String): JsonElement = savedTracks

    override suspend fun fetchTracks(accessToken: String, trackIds: List<String>): JsonArray = tracks

    override suspend fun fetchTrackDownloadInfo(accessToken: String, trackId: String): JsonArray = trackDownloadInfo

    override suspend fun fetchTrackFileInfo(accessToken: String, trackId: String): JsonObject = JsonObject(emptyMap())

    override suspend fun fetchDownloadInfoUrl(accessToken: String, url: String): String = downloadInfoUrlResponse
}
