package com.mplayeraudio.feature.library

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun MusicLibraryRoute(
    modifier: Modifier = Modifier,
    viewModel: MusicLibraryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedDestination by remember { mutableStateOf<LibraryDestination?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MusicLibraryEffect.NavigateToPlaylist -> {
                    selectedDestination = LibraryDestination.Playlist(effect.playlistId)
                }
            }
        }
    }

    Crossfade(
        targetState = selectedDestination,
        label = "library-nav",
    ) { destination ->
        when (destination) {
            null -> {
                MusicLibraryScreen(
                    state = state,
                    onPlaylistClick = viewModel::onPlaylistClick,
                    modifier = modifier,
                )
            }
            is LibraryDestination.Playlist -> {
                val playlistTitle = state.content?.cards
                    ?.filterIsInstance<MusicLibraryCard.Playlist>()
                    ?.firstOrNull { it.id == destination.playlistId }
                    ?.title
                    ?: stringResource(R.string.playlist_detail_title)

                PlaylistDetailRoute(
                    title = playlistTitle,
                    onBack = { selectedDestination = null },
                    modifier = modifier,
                )
            }
        }
    }
}

private sealed interface LibraryDestination {
    data class Playlist(val playlistId: PlaylistId) : LibraryDestination
}
