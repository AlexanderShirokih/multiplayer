package com.mplayeraudio.services.userplaylists

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.core.domain.musiclibrary.UserPlaylistTrackId
import com.mplayeraudio.services.userplaylists.data.UserPlaylistDao

class UserPlaylistsTrackStreamUrlProvider(
    private val dao: UserPlaylistDao,
) : TrackStreamUrlProvider {
    override suspend fun getStreamUrl(trackId: TrackId): String {
        val userPlaylistTrackId = trackId as? UserPlaylistTrackId
            ?: throw MusicLibraryException.ProviderError(
                code = "invalid-track-id",
                description = "Track ID is not a user playlist database ID.",
            )

        return dao.getTrackUrl(userPlaylistTrackId.value)
            ?: throw MusicLibraryException.ProviderError(
                code = "track-not-found",
                description = "User playlist track was not found.",
            )
    }
}
