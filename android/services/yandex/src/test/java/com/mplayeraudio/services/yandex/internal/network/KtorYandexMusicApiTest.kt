package com.mplayeraudio.services.yandex.internal.network

import org.junit.Assert.assertEquals
import org.junit.Test

class KtorYandexMusicApiTest {

    @Test
    fun `buildStreamingDownloadInfoRequest creates expected signed query`() {
        val request = buildStreamingDownloadInfoRequest(
            trackId = "565378",
            timestampSeconds = 1_668_329_810,
            signingSecret = "p93jhgh689SBReK6ghtw62",
            clientHeader = "YandexMusicAndroid/24022571",
        )

        assertEquals("true", request.parameters["can_use_streaming"])
        assertEquals("1668329810", request.parameters["ts"])
        assertEquals(
            "5j8wlO7h6lcauv86ieL5JRkprxDjXe82YlYrGU1XY80=",
            request.parameters["sign"],
        )
        assertEquals(
            "YandexMusicAndroid/24022571",
            request.headers["X-Yandex-Music-Client"],
        )
    }

    @Test
    fun `buildTrackFileInfoRequest creates expected signed query`() {
        val request = buildTrackFileInfoRequest(
            trackId = "117708948",
            timestampSeconds = 1_724_399_849,
            signingSecret = "kzqU4XhfCaY6B6JTHODeq5",
            clientHeader = "YandexMusicDesktopAppWindows/5.13.2",
        )

        assertEquals("1724399849", request.parameters["ts"])
        assertEquals("117708948", request.parameters["trackId"])
        assertEquals("lossless", request.parameters["quality"])
        assertEquals("mp3", request.parameters["codecs"])
        assertEquals("raw", request.parameters["transports"])
        assertEquals(
            "txWtJQzNZhIvspyV1oHxAGzskPdVWvNhLjr+iZTlkzk",
            request.parameters["sign"],
        )
        assertEquals(
            "YandexMusicDesktopAppWindows/5.13.2",
            request.headers["X-Yandex-Music-Client"],
        )
    }
}
