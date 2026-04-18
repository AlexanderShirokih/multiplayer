package com.mplayeraudio.app

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mplayeraudio.feature.auth.AuthWelcomeRoute
import com.mplayeraudio.feature.library.MusicLibraryRoute
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppRootRoute(
    modifier: Modifier = Modifier,
    viewModel: AppRootViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Crossfade(
        targetState = state.destination,
        label = "app-screen",
    ) { destination ->
        when (destination) {
            AppDestination.Auth -> AuthWelcomeRoute(
                onListenLocalClick = viewModel::openDeviceOnlyMode,
                modifier = modifier,
            )
            AppDestination.Main -> MusicLibraryRoute(modifier = modifier)
        }
    }
}
