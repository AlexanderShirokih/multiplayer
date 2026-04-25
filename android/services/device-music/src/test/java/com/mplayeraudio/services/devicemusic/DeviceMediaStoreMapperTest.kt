package com.mplayeraudio.services.devicemusic

import com.mplayeraudio.core.domain.musiclibrary.DeviceTrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMediaStoreMapperTest {

    @Test
    fun `maps media store row into playlist track entry`() {
        val mapped = requireNotNull(
            DeviceMediaStoreMapper.mapRow(
                DeviceMediaStoreRow(
                    mediaId = 42L,
                    albumId = 77L,
                    title = "Local Track",
                    artistName = "Local Artist",
                    durationMs = 180_000L,
                    position = 0,
                ),
            ),
        )

        assertEquals(42L, mapped.mediaId)
        assertEquals("content://media/external/audio/media/42", mapped.contentUri)
        assertEquals(DeviceTrackId(42L), mapped.entry.trackRef.trackId)
        assertEquals("Local Track", mapped.entry.track?.preview?.title)
        assertEquals("Local Artist", mapped.entry.track?.preview?.artists?.single()?.name)
        assertEquals(
            "content://media/external/audio/albumart/77",
            mapped.entry.track?.preview?.coverUriTemplate,
        )
    }

    @Test
    fun `filters out entries with empty artist`() {
        val mapped = DeviceMediaStoreMapper.mapRow(
            DeviceMediaStoreRow(
                mediaId = 99L,
                albumId = null,
                title = "Some title",
                artistName = "",
                durationMs = 45_000L,
                position = 0,
            ),
        )

        assertNull(mapped)
    }

    @Test
    fun `filters out entries with unknown artist`() {
        val mapped = DeviceMediaStoreMapper.mapRow(
            DeviceMediaStoreRow(
                mediaId = 100L,
                albumId = null,
                title = "Some title",
                artistName = "<unknown>",
                durationMs = 10_000L,
                position = 0,
            ),
        )

        assertNull(mapped)
    }

    @Test
    fun `filters out entries without title`() {
        val mapped = DeviceMediaStoreMapper.mapRow(
            DeviceMediaStoreRow(
                mediaId = 101L,
                albumId = null,
                title = "",
                artistName = "Artist",
                durationMs = 8_000L,
                position = 0,
            ),
        )

        assertNull(mapped)
    }

    @Test
    fun `keeps track with artist`() {
        val mapped = DeviceMediaStoreMapper.mapRow(
            DeviceMediaStoreRow(
                mediaId = 102L,
                albumId = null,
                title = "Track",
                artistName = "Some Artist",
                durationMs = 180_000L,
                position = 0,
            ),
        )

        requireNotNull(mapped)
    }
}
