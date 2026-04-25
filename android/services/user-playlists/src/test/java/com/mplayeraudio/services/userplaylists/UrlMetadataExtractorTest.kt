package com.mplayeraudio.services.userplaylists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlMetadataExtractorTest {

    private val extractor = UrlMetadataExtractor()

    @Test
    fun `parse valid http url`() {
        val result = extractor.parse("http://example.com/audio/track.mp3")
        assertEquals("track.mp3", result?.title)
        assertEquals("example.com", result?.artist)
    }

    @Test
    fun `parse valid https url`() {
        val result = extractor.parse("https://my-server.org/music/song.flac")
        assertEquals("song.flac", result?.title)
        assertEquals("my-server.org", result?.artist)
    }

    @Test
    fun `parse invalid url`() {
        val result = extractor.parse("not a url")
        assertNull(result)
    }

    @Test
    fun `parse url without path`() {
        val result = extractor.parse("https://example.com")
        assertEquals("Unknown Track", result?.title)
        assertEquals("example.com", result?.artist)
    }

    @Test
    fun `parse url with trailing slash`() {
        val result = extractor.parse("https://example.com/audio/")
        assertEquals("audio", result?.title)
        assertEquals("example.com", result?.artist)
    }

    @Test
    fun `parse non-http scheme`() {
        val result = extractor.parse("ftp://example.com/audio.mp3")
        assertNull(result)
    }
}
