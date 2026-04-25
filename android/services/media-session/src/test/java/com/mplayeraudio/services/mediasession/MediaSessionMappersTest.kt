package com.mplayeraudio.services.mediasession

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.UserPlaylistTrackId
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaSessionMappersTest {

    @Test
    fun `media item round trip preserves track id separately from queue item id`() {
        val queueItem = PlaybackQueueItem(
            id = "0:e3e3a15a-19e2-4397-ab36-e8e0dd557433",
            trackId = UserPlaylistTrackId(1L),
            source = PlayableSource.Remote(MusicProviderId.UserPlaylists),
            title = "Track",
            subtitle = "Local stream",
            durationMs = 0L,
        )

        val restoredQueueItem = queueItem.toMediaItem().toQueueItem()

        assertEquals(queueItem.id, restoredQueueItem.id)
        assertEquals(UserPlaylistTrackId(1L), restoredQueueItem.trackId)
    }
}
