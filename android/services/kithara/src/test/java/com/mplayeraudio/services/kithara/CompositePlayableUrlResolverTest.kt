package com.mplayeraudio.services.kithara

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlaybackQueueItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositePlayableUrlResolverTest {

    @Test
    fun `local source returns uri without delegating`() = runTest {
        val resolver = CompositePlayableUrlResolver(
            providers = mapOf(
                MusicProviderId.YandexMusic to RecordingTrackStreamUrlProvider("https://example.test"),
            ),
        )

        val result = resolver.getPlayableUrl(
            PlaybackQueueItem(
                id = "1",
                trackId = TrackId("device:1"),
                source = PlayableSource.Local("content://media/external/audio/media/1"),
                title = "Track",
                subtitle = "Artist",
                durationMs = 1000L,
            ),
        )

        assertEquals("content://media/external/audio/media/1", result)
    }

    @Test
    fun `remote source delegates to provider resolver`() = runTest {
        val provider = RecordingTrackStreamUrlProvider("https://stream.example/42.mp3")
        val resolver = CompositePlayableUrlResolver(
            providers = mapOf(MusicProviderId.YandexMusic to provider),
        )

        val result = resolver.getPlayableUrl(
            PlaybackQueueItem(
                id = "1",
                trackId = TrackId("42"),
                source = PlayableSource.Remote(MusicProviderId.YandexMusic),
                title = "Track",
                subtitle = "Artist",
                durationMs = 1000L,
            ),
        )

        assertEquals("https://stream.example/42.mp3", result)
        assertEquals(TrackId("42"), provider.requestedTrackId)
    }
}

private class RecordingTrackStreamUrlProvider(
    private val response: String,
) : TrackStreamUrlProvider {
    var requestedTrackId: TrackId? = null

    override suspend fun getStreamUrl(trackId: TrackId): String {
        requestedTrackId = trackId
        return response
    }
}
