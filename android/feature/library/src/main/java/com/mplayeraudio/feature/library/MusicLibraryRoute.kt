package com.mplayeraudio.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                is MusicLibraryEffect.NavigateToTrackList -> {
                    selectedDestination = LibraryDestination.TrackList(effect.destination)
                }
            }
        }
    }

    BackHandler(enabled = selectedDestination != null) {
        selectedDestination = null
    }

    Crossfade(
        targetState = selectedDestination,
        label = "library-nav",
    ) { destination ->
        when (destination) {
            null -> {
                MusicLibraryScreen(
                    state = state,
                    onRefresh = viewModel::onRefresh,
                    onPlaylistClick = viewModel::onPlaylistClick,
                    modifier = modifier,
                )
            }
            is LibraryDestination.TrackList -> {
                TrackListRoute(
                    destination = destination.destination,
                    onBack = { selectedDestination = null },
                    modifier = modifier,
                )
            }
        }
    }
}

private sealed interface LibraryDestination {
    data class TrackList(val destination: LibraryTrackListDestination) : LibraryDestination
}
