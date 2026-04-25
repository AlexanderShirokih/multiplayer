package com.mplayeraudio.services.yandex

import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.core.domain.yandexauth.YandexAccessTokenProvider
import com.mplayeraudio.services.yandex.internal.YandexMusicRequestRunner
import com.mplayeraudio.services.yandex.internal.network.YandexMusicApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

class YandexTrackStreamUrlProviderTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `getStreamUrl prefers direct streaming entry over legacy preview xml`() = runTest {
        val api = FakeTrackStreamUrlApi(
            trackFileInfo = jsonObject(
                """
                {
                  "downloadInfo": {
                    "trackId": "565378",
                    "quality": "lossless",
                    "codec": "mp3",
                    "bitrate": 320,
                    "transport": "raw",
                    "url": "https://strm.yandex.ru/music-v2/raw/full-track.mp3"
                  }
                }
                """.trimIndent(),
            ),
            trackDownloadInfo = jsonArray(
                """
                [
                  {
                    "codec": "mp3",
                    "bitrateInKbps": 128,
                    "preview": false,
                    "downloadInfoUrl": "https://storage.mds.yandex.net/file-download-info/id/preview?sign=test",
                    "direct": false
                  },
                  {
                    "codec": "mp3",
                    "bitrateInKbps": 320,
                    "preview": false,
                    "downloadInfoUrl": "https://strm.yandex.ru/music/music-strm-jsons/565378/master.m3u8?abc_id=94&from=ya-music",
                    "direct": true,
                    "container": "hls"
                  }
                ]
                """.trimIndent(),
            ),
        )

        val provider = provider(api)
        val streamUrl = provider.getStreamUrl(YandexTrackId("565378"))

        assertEquals(
            "https://strm.yandex.ru/music-v2/raw/full-track.mp3",
            streamUrl,
        )
        assertTrue(api.downloadInfoUrlRequests.isEmpty())
    }

    @Test
    fun `getStreamUrl falls back to signed xml link when direct stream is unavailable`() = runTest {
        val api = FakeTrackStreamUrlApi(
            trackDownloadInfo = jsonArray(
                """
                [
                  {
                    "codec": "mp3",
                    "bitrateInKbps": 128,
                    "preview": false,
                    "downloadInfoUrl": "https://storage.mds.yandex.net/file-download-info/id/full?sign=test",
                    "direct": false
                  }
                ]
                """.trimIndent(),
            ),
            downloadInfoUrlResponse = """
                <download-info>
                  <host>example.storage.yandex.net</host>
                  <path>/abc/track.mp3</path>
                  <ts>12345</ts>
                  <s>salt</s>
                </download-info>
            """.trimIndent(),
        )

        val provider = provider(api)
        val streamUrl = provider.getStreamUrl(YandexTrackId("42"))

        assertEquals(
            "https://example.storage.yandex.net/get-mp3/8fbc459932a68898f42f84a5390aa7f2/12345/abc/track.mp3",
            streamUrl,
        )
        assertEquals(
            listOf("https://storage.mds.yandex.net/file-download-info/id/full?sign=test"),
            api.downloadInfoUrlRequests,
        )
    }

    private fun provider(api: FakeTrackStreamUrlApi): YandexTrackStreamUrlProvider {
        return YandexTrackStreamUrlProvider(
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

    private fun jsonArray(value: String): JsonArray = json.parseToJsonElement(value).jsonArray

    private fun jsonObject(value: String): JsonObject = json.parseToJsonElement(value).jsonObject
}

private class FakeTrackStreamUrlApi(
    private val trackFileInfo: JsonObject? = null,
    private val trackDownloadInfo: JsonArray,
    private val downloadInfoUrlResponse: String = "",
) : YandexMusicApi {
    val downloadInfoUrlRequests = mutableListOf<String>()

    override suspend fun fetchAvailability(accessToken: String): JsonObject = JsonObject(emptyMap())

    override suspend fun fetchCurrentUser(accessToken: String): JsonObject = JsonObject(emptyMap())

    override suspend fun fetchOwnPlaylists(accessToken: String, userId: String): JsonArray = JsonArray(emptyList())

    override suspend fun fetchPlaylist(accessToken: String, userId: String, kind: Long): JsonObject =
        JsonObject(emptyMap())

    override suspend fun fetchSavedTracks(accessToken: String, userId: String): JsonElement =
        JsonObject(emptyMap())

    override suspend fun fetchTracks(accessToken: String, trackIds: List<String>): JsonArray = JsonArray(emptyList())

    override suspend fun fetchTrackDownloadInfo(accessToken: String, trackId: String): JsonArray =
        trackDownloadInfo

    override suspend fun fetchTrackFileInfo(accessToken: String, trackId: String): JsonObject {
        return trackFileInfo ?: throw com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException.ProviderError(
            code = "not-implemented",
            description = "file-info unavailable",
        )
    }

    override suspend fun fetchDownloadInfoUrl(accessToken: String, url: String): String {
        downloadInfoUrlRequests += url
        return downloadInfoUrlResponse
    }
}
