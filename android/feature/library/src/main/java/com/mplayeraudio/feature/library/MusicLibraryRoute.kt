package com.mplayeraudio.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is MusicLibraryEffect.NavigateToTrackList -> {
                    selectedDestination = effect.toLibraryDestination()
                }
                is MusicLibraryEffect.NavigateToPlaylistEditor -> {
                    selectedDestination = effect.toLibraryDestination()
                }
                is MusicLibraryEffect.ShowError -> {
                    snackbarHostState.showSnackbar("Ошибка")
                }
            }
        }
    }

    BackHandler(enabled = selectedDestination != null) {
        selectedDestination = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        Crossfade(
            targetState = selectedDestination,
            label = "library-nav",
            modifier = Modifier.padding(paddingValues),
        ) { destination ->
            when (destination) {
                null -> {
                    MusicLibraryScreen(
                        state = state,
                        onRefresh = viewModel::onRefresh,
                        onPlaylistClick = viewModel::onPlaylistClick,
                        onCreatePlaylistClick = viewModel::onCreatePlaylistClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is LibraryDestination.TrackList -> {
                    TrackListRoute(
                        destination = destination.destination,
                        onBack = { selectedDestination = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

internal fun MusicLibraryEffect.toLibraryDestination(): LibraryDestination? {
    return when (this) {
        is MusicLibraryEffect.NavigateToTrackList -> LibraryDestination.TrackList(destination)
        is MusicLibraryEffect.NavigateToPlaylistEditor -> LibraryDestination.TrackList(destination)
        is MusicLibraryEffect.ShowError -> null
    }
}

internal sealed interface LibraryDestination {
    data class TrackList(val destination: LibraryTrackListDestination) : LibraryDestination
}
