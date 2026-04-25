package com.mplayeraudio.services.mediasession

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.services.kithara.AudioEngineState
import com.mplayeraudio.services.kithara.AudioEngineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerSnapshotEngineStateTest {

    @Test
    fun `engine state does not move failed snapshot out of idle`() {
        val error = PlaybackException(
            "Load failed",
            IllegalStateException("broken stream"),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        val snapshot = PlayerSnapshot(
            queue = listOf(queueItem),
            currentIndex = 0,
            playbackState = Player.STATE_IDLE,
            playerError = error,
        )

        val nextSnapshot = snapshot.applyEngineState(
            AudioEngineState(
                status = AudioEngineStatus.ReadyToPlay,
                currentItemId = queueItem.id,
                isPlaying = true,
            ),
        )

        assertEquals(Player.STATE_IDLE, nextSnapshot.playbackState)
        assertFalse(nextSnapshot.isPlaying)
        assertSame(error, nextSnapshot.playerError)
    }
}

private val queueItem = PlaybackQueueItem(
    id = "0:track",
    trackId = YandexTrackId("track"),
    source = PlayableSource.Remote(MusicProviderId.YandexMusic),
    title = "Track",
    subtitle = "Artist",
    durationMs = 180_000L,
)
