package com.mplayeraudio.core.data

import com.mplayeraudio.core.domain.musiclibrary.MusicLibrary
import com.mplayeraudio.core.domain.musiclibrary.MusicProvider
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRef
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class DefaultMusicLibrary(
    providers: Set<MusicProvider>,
) : MusicLibrary {

    private val providersById = providers.associateBy(MusicProvider::id)
    private val orderedProviders = providers.sortedBy(MusicProvider::id)

    override fun observeAvailability(): Flow<MusicServiceAvailability> {
        if (orderedProviders.isEmpty()) {
            return flowOf(
                MusicServiceAvailability(
                    isAvailable = false,
                    region = null,
                    permissions = emptySet(),
                ),
            )
        }

        return combine(orderedProviders.map(MusicProvider::observeAvailability)) { availability ->
            availability.fold(
                MusicServiceAvailability(
                    isAvailable = false,
                    region = null,
                    permissions = emptySet(),
                ),
            ) { acc, next ->
                MusicServiceAvailability(
                    isAvailable = acc.isAvailable || next.isAvailable,
                    region = acc.region ?: next.region,
                    permissions = acc.permissions + next.permissions,
                )
            }
        }
    }

    override fun observeAllPlaylists(): Flow<List<PlaylistSummary>> {
        if (orderedProviders.isEmpty()) {
            return flowOf(emptyList())
        }

        return combine(orderedProviders.map(MusicProvider::observePlaylists)) { playlists ->
            playlists
                .asList()
                .flatten()
                .sortedWith(
                    compareBy<PlaylistSummary>(
                        { it.provider.ordinal },
                        PlaylistSummary::title,
                    ),
                )
        }
    }

    override fun observePlaylist(ref: PlaylistRef): Flow<Playlist?> {
        return provider(ref.provider).observePlaylist(ref.id)
    }

    override fun observeSavedTracks(): Flow<SavedTracksResult> {
        val savedTracksProviders = orderedProviders.filter { it.id == MusicProviderId.YandexMusic }
        if (savedTracksProviders.isEmpty()) {
            return flowOf(SavedTracksResult.PrivateLibrary)
        }

        return combine(savedTracksProviders.map(MusicProvider::observeSavedTracks)) { results ->
            results.firstOrNull { it !is SavedTracksResult.PrivateLibrary } ?: SavedTracksResult.PrivateLibrary
        }
    }

    override suspend fun refreshAll() {
        coroutineScope {
            orderedProviders.map { provider ->
                async {
                    provider.refreshAvailability()
                    provider.refreshPlaylists()
                }
            }.awaitAll()
        }
    }

    override suspend fun refreshPlaylist(ref: PlaylistRef) {
        provider(ref.provider).refreshPlaylist(ref.id)
    }

    override suspend fun refreshSavedTracks() {
        coroutineScope {
            orderedProviders.map { provider ->
                async { provider.refreshSavedTracks() }
            }.awaitAll()
        }
    }

    private fun provider(id: MusicProviderId): MusicProvider {
        return checkNotNull(providersById[id]) {
            "Music provider $id is not registered."
        }
    }
}
