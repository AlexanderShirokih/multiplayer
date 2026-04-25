package com.mplayeraudio.feature.library

import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRef
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRole
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLibraryRouteTest {

    @Test
    fun `playlist editor effect opens track list in editing mode`() {
        val destination = LibraryTrackListDestination(
            ref = PlaylistRef(
                provider = MusicProviderId.UserPlaylists,
                id = PlaylistId(
                    ownerId = ProviderUserId("owner"),
                    kind = PlaylistKind(1L),
                ),
            ),
            title = "Новый плейлист",
            role = PlaylistRole.Regular,
            initiallyEditing = true,
        )

        val routeDestination = MusicLibraryEffect.NavigateToPlaylistEditor(destination)
            .toLibraryDestination()

        assertEquals(LibraryDestination.TrackList(destination), routeDestination)
        val trackList = routeDestination as LibraryDestination.TrackList
        assertTrue(trackList.destination.initiallyEditing)
    }
}
