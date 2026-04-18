package com.mplayeraudio.services.devicemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMediaStoreMapperTest {

    @Test
    fun `maps media store row into playlist track entry`() {
        val mapped = requireNotNull(
            DeviceMediaStoreMapper.mapRow(
                mediaId = 42L,
                title = "Local Track",
                artistName = "Local Artist",
                durationMs = 180_000L,
                position = 0,
            ),
        )

        assertEquals(42L, mapped.mediaId)
        assertEquals("content://media/external/audio/media/42", mapped.contentUri)
        assertEquals("device:42", mapped.entry.trackRef.trackId.value)
        assertEquals("Local Track", mapped.entry.track?.preview?.title)
        assertEquals("Local Artist", mapped.entry.track?.preview?.artists?.single()?.name)
    }

    @Test
    fun `filters out entries with empty artist`() {
        val mapped = DeviceMediaStoreMapper.mapRow(
            mediaId = 99L,
            title = "Some title",
            artistName = "",
            durationMs = 45_000L,
            position = 0,
        )

        assertNull(mapped)
    }

    @Test
    fun `filters out entries with unknown artist`() {
        val mapped = DeviceMediaStoreMapper.mapRow(
            mediaId = 100L,
            title = "Some title",
            artistName = "<unknown>",
            durationMs = 10_000L,
            position = 0,
        )

        assertNull(mapped)
    }

    @Test
    fun `filters out entries without title`() {
        val mapped = DeviceMediaStoreMapper.mapRow(
            mediaId = 101L,
            title = "",
            artistName = "Artist",
            durationMs = 8_000L,
            position = 0,
        )

        assertNull(mapped)
    }

    @Test
    fun `keeps track with artist`() {
        val mapped = DeviceMediaStoreMapper.mapRow(
            mediaId = 102L,
            title = "Track",
            artistName = "Some Artist",
            durationMs = 180_000L,
            position = 0,
        )

        requireNotNull(mapped)
    }
}
