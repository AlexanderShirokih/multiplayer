package com.mplayeraudio.services.yandex

import com.mplayeraudio.core.domain.musiclibrary.MusicProvider
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import com.mplayeraudio.services.yandex.internal.YandexMusicRequestRunner
import com.mplayeraudio.services.yandex.internal.toAvailability
import com.mplayeraudio.services.yandex.internal.toCurrentUserId
import com.mplayeraudio.services.yandex.internal.toPlaylist
import com.mplayeraudio.services.yandex.internal.toPlaylistSummary
import com.mplayeraudio.services.yandex.internal.toSavedTracksResult
import com.mplayeraudio.services.yandex.internal.network.YandexMusicApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.jsonObject

internal class YandexMusicProvider(
    private val requestRunner: YandexMusicRequestRunner,
    private val api: YandexMusicApi,
) : MusicProvider {
    override val id: MusicProviderId = MusicProviderId.YandexMusic

    private val availabilityState = MutableStateFlow<MusicServiceAvailability?>(null)
    private val playlistsState = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    private val playlistState = MutableStateFlow<Map<PlaylistId, Playlist>>(emptyMap())
    private val savedTracksState = MutableStateFlow<SavedTracksResult?>(null)

    override fun observeAvailability(): Flow<MusicServiceAvailability> {
        return availabilityState.filterNotNull()
    }

    override fun observePlaylists(): Flow<List<PlaylistSummary>> {
        return playlistsState
    }

    override fun observePlaylist(id: PlaylistId): Flow<Playlist?> {
        return playlistState.map { playlists -> playlists[id] }
    }

    override fun observeSavedTracks(): Flow<SavedTracksResult> {
        return savedTracksState.filterNotNull()
    }

    override suspend fun refreshAvailability() {
        availabilityState.value = requestRunner.withAuthorizedRequest { accessToken ->
            api.fetchAvailability(accessToken).toAvailability()
        }
    }

    override suspend fun refreshPlaylists() {
        playlistsState.value = requestRunner.withCurrentUserId { accessToken, userId ->
            api.fetchOwnPlaylists(
                accessToken = accessToken,
                userId = userId.value,
            ).map { element -> element.jsonObject.toPlaylistSummary() }
        }
    }

    override suspend fun refreshPlaylist(id: PlaylistId) {
        val playlist = requestRunner.withAuthorizedRequest { accessToken ->
            api.fetchPlaylist(
                accessToken = accessToken,
                userId = id.ownerId.value,
                kind = id.kind.value,
            ).toPlaylist()
        }
        playlistState.value = playlistState.value + (id to playlist)
    }

    override suspend fun refreshSavedTracks() {
        savedTracksState.value = requestRunner.withCurrentUserId { accessToken, userId ->
            api.fetchSavedTracks(
                accessToken = accessToken,
                userId = userId.value,
            ).toSavedTracksResult()
        }
    }
}
