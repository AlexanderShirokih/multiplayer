package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.YandexTrackId
import com.mplayeraudio.core.player.NowPlayingStripState
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlaybackQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackListRouteTest {

    @Test
    fun `empty editable playlist shows track list content instead of retry feedback`() {
        val state = TrackListRouteState(
            title = "Новый плейлист",
            tracks = emptyList(),
            isLoading = false,
            status = TrackListStatus.Empty,
            isEditing = true,
            canEdit = true,
        )

        assertTrue(state.shouldShowTrackListContent())
    }

    @Test
    fun `empty non editable playlist keeps feedback state`() {
        val state = TrackListRouteState(
            title = "Любимые",
            tracks = emptyList(),
            isLoading = false,
            status = TrackListStatus.Empty,
            canEdit = false,
        )

        assertFalse(state.shouldShowTrackListContent())
    }

    @Test
    fun `screen state marks equalizer active only for playing track`() {
        val state = TrackListRouteState(
            title = "Road Trip",
            tracks = listOf(
                trackItem(id = "0:first", position = 1),
                trackItem(id = "1:second", position = 2),
            ),
            activeTrackIndex = 0,
            playingTrackIndex = null,
            isLoading = false,
        )

        val stoppedState = state.toScreenState(NowPlayingStripState(title = "First"))
        assertEquals("First", stoppedState.nowPlaying?.title)
        assertFalse(stoppedState.tracks[0].isActive)
        assertFalse(stoppedState.tracks[1].isActive)

        val playingState = state.copy(playingTrackIndex = 1)
            .toScreenState(NowPlayingStripState(title = "Second"))
        assertFalse(playingState.tracks[0].isActive)
        assertTrue(playingState.tracks[1].isActive)
    }
}

private fun trackItem(
    id: String,
    position: Int,
): TrackListItemState {
    return TrackListItemState(
        queueItem = PlaybackQueueItem(
            id = id,
            trackId = YandexTrackId(id),
            source = PlayableSource.Remote(MusicProviderId.YandexMusic),
            title = id,
            subtitle = "Artist",
            durationMs = 180_000L,
        ),
        title = id,
        artist = "Artist",
        duration = "3:00",
        trackPosition = position,
    )
}
