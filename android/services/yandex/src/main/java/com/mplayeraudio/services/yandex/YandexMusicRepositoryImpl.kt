package com.mplayeraudio.services.yandex

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryRepository
import com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import com.mplayeraudio.core.domain.musiclibrary.Track
import com.mplayeraudio.core.domain.musiclibrary.TrackRef
import com.mplayeraudio.core.domain.yandexauth.YandexAccessTokenProvider
import com.mplayeraudio.services.yandex.internal.toApiTrackId
import com.mplayeraudio.services.yandex.internal.toAvailability
import com.mplayeraudio.services.yandex.internal.toCurrentUserId
import com.mplayeraudio.services.yandex.internal.toPlaylist
import com.mplayeraudio.services.yandex.internal.toPlaylistSummary
import com.mplayeraudio.services.yandex.internal.toSavedTracksResult
import com.mplayeraudio.services.yandex.internal.toTrack
import com.mplayeraudio.services.yandex.internal.network.YandexMusicApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.jsonObject

internal class YandexMusicRepositoryImpl(
    private val accessTokenProvider: YandexAccessTokenProvider,
    private val api: YandexMusicApi,
) : MusicLibraryRepository {

    private val requestRunner = YandexMusicRequestRunner(
        accessTokenProvider = accessTokenProvider,
        api = api,
    )
    private val availabilityState = MutableStateFlow<MusicServiceAvailability?>(null)
    private val ownPlaylistsState = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    private val playlistState = MutableStateFlow<Map<PlaylistId, Playlist>>(emptyMap())
    private val savedTracksState = MutableStateFlow<SavedTracksResult?>(null)
    private val trackState = MutableStateFlow<Map<TrackRef, Track>>(emptyMap())

    override fun observeAvailability(): Flow<MusicServiceAvailability> {
        return availabilityState.filterNotNull()
    }

    override fun observeOwnPlaylists(): Flow<List<PlaylistSummary>> {
        return ownPlaylistsState
    }

    override fun observePlaylist(id: PlaylistId): Flow<Playlist?> {
        return playlistState.map { playlists -> playlists[id] }
    }

    override fun observeSavedTracks(): Flow<SavedTracksResult> {
        return savedTracksState.filterNotNull()
    }

    override fun observeTracks(refs: List<TrackRef>): Flow<List<Track>> {
        return trackState.map { tracks ->
            refs.mapNotNull(tracks::get)
        }
    }

    override suspend fun refreshAvailability() {
        availabilityState.value = requestRunner.withAuthorizedRequest { accessToken ->
            api.fetchAvailability(accessToken).toAvailability()
        }
    }

    override suspend fun refreshOwnPlaylists() {
        ownPlaylistsState.value = requestRunner.withCurrentUserId { accessToken, userId ->
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
        cacheTracks(playlist.tracks.mapNotNull { entry -> entry.track })
    }

    override suspend fun refreshSavedTracks() {
        savedTracksState.value = requestRunner.withCurrentUserId { accessToken, userId ->
            api.fetchSavedTracks(
                accessToken = accessToken,
                userId = userId.value,
            ).toSavedTracksResult()
        }
    }

    override suspend fun refreshTracks(refs: List<TrackRef>) {
        if (refs.isEmpty()) {
            return
        }

        val loadedTracks = requestRunner.withAuthorizedRequest { accessToken ->
            api.fetchTracks(
                accessToken = accessToken,
                trackIds = refs.map(TrackRef::toApiTrackId),
            ).map { element -> element.jsonObject.toTrack() }
        }

        val updatedTracks = trackState.value.toMutableMap()
        loadedTracks.forEach { track ->
            updatedTracks[track.preview.ref] = track
        }
        trackState.value = updatedTracks
    }

    private fun cacheTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) {
            return
        }

        val updatedTracks = trackState.value.toMutableMap()
        tracks.forEach { track ->
            updatedTracks[track.preview.ref] = track
        }
        trackState.value = updatedTracks
    }
}

private class YandexMusicRequestRunner(
    private val accessTokenProvider: YandexAccessTokenProvider,
    private val api: YandexMusicApi,
) {

    @Volatile
    private var cachedUserId: ProviderUserId? = null

    suspend fun <T> withCurrentUserId(
        block: suspend (accessToken: String, userId: ProviderUserId) -> T,
    ): T {
        return withAuthorizedRequest { accessToken ->
            block(accessToken, requireCurrentUserId(accessToken))
        }
    }

    suspend fun <T> withAuthorizedRequest(
        block: suspend (accessToken: String) -> T,
    ): T {
        val currentToken = accessTokenProvider.getValidAccessToken(forceRefresh = false)
        return try {
            block(currentToken)
        } catch (_: MusicLibraryException.Unauthorized) {
            val refreshedToken = accessTokenProvider.getValidAccessToken(forceRefresh = true)
            block(refreshedToken)
        }
    }

    private suspend fun requireCurrentUserId(accessToken: String): ProviderUserId {
        cachedUserId?.let { return it }
        val userId = api.fetchCurrentUser(accessToken).toCurrentUserId()
        cachedUserId = userId
        return userId
    }
}
