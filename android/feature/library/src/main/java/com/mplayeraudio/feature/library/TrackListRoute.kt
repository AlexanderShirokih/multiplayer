package com.mplayeraudio.feature.library

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.player.NowPlayingStripViewModel
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.ui.components.MultiplayerBrandBackgroundDecor
import com.mplayeraudio.core.ui.components.MultiplayerSurface
import com.mplayeraudio.core.ui.components.MultiplayerText
import com.mplayeraudio.core.ui.model.MultiplayerTrackListItemState
import com.mplayeraudio.core.ui.theme.MultiplayerTheme
import com.mplayeraudio.services.devicemusic.MediaAudioPermission
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
fun TrackListRoute(
    destination: LibraryTrackListDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playbackBridge: PlaybackQueueBridge = koinInject(),
    viewModel: TrackListViewModel = koinViewModel(
        key = destination.ref.toString(),
        parameters = { parametersOf(destination) },
    ),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDevicePlaylist = destination.ref.provider == MusicProviderId.Device
    val routeState by viewModel.state.collectAsStateWithLifecycle()
    var permissionState by remember(destination.ref) {
        mutableStateOf(
            if (!isDevicePlaylist || MediaAudioPermission.hasPermission(context)) {
                DevicePermissionUiState.Granted
            } else {
                DevicePermissionUiState.Requesting
            },
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionState = if (granted) {
            viewModel.onRetry()
            DevicePermissionUiState.Granted
        } else {
            DevicePermissionUiState.Denied
        }
    }
    val nowPlayingViewModel = remember(destination, playbackBridge) {
        NowPlayingStripViewModel(playbackBridge)
    }
    val nowPlayingState by nowPlayingViewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(nowPlayingViewModel) {
        onDispose(nowPlayingViewModel::dispose)
    }

    DisposableEffect(lifecycleOwner, destination.ref, isDevicePlaylist) {
        if (!isDevicePlaylist) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && permissionState == DevicePermissionUiState.Denied) {
                    val isGranted = MediaAudioPermission.hasPermission(context)
                    permissionState = if (isGranted) {
                        viewModel.onRetry()
                        DevicePermissionUiState.Granted
                    } else {
                        DevicePermissionUiState.Denied
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(destination.ref, permissionState) {
        if (isDevicePlaylist && permissionState == DevicePermissionUiState.Requesting) {
            permissionLauncher.launch(MediaAudioPermission.requiredPermission())
        }
    }

    when {
        isDevicePlaylist && permissionState == DevicePermissionUiState.Denied -> {
            TrackListFeedbackScreen(
                title = routeState.title,
                headline = stringResource(R.string.device_permission_denied_title),
                message = stringResource(R.string.device_permission_denied_description),
                onBack = onBack,
                modifier = modifier,
            ) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                ) {
                    MultiplayerText(
                        text = stringResource(R.string.device_permission_denied_cta),
                        style = MultiplayerTheme.typography.label,
                        color = MultiplayerTheme.colors.accent,
                    )
                }
            }
        }

        routeState.tracks.isNotEmpty() -> {
            TrackListScreen(
                state = TrackListScreenState(
                    title = routeState.title,
                    nowPlaying = if (routeState.activeTrackIndex != null) nowPlayingState else null,
                    tracks = routeState.tracks.mapIndexed { index, track ->
                        MultiplayerTrackListItemState(
                            title = track.title,
                            artist = track.artist,
                            duration = track.duration,
                            trackPosition = track.trackPosition,
                            isActive = routeState.activeTrackIndex == index,
                        )
                    },
                ),
                onNowPlayingAction = nowPlayingViewModel::onAction,
                onTrackClick = viewModel::onTrackClick,
                onBack = onBack,
                modifier = modifier,
            )
        }

        routeState.isLoading -> {
            TrackListFeedbackScreen(
                title = routeState.title,
                headline = null,
                message = stringResource(R.string.track_list_loading_message),
                onBack = onBack,
                modifier = modifier,
            ) {
                CircularProgressIndicator(
                    color = MultiplayerTheme.colors.accent,
                )
            }
        }

        else -> {
            TrackListFeedbackScreen(
                title = routeState.title,
                headline = null,
                message = routeState.status.toMessage(),
                onBack = onBack,
                modifier = modifier,
            ) {
                TextButton(onClick = viewModel::onRetry) {
                    MultiplayerText(
                        text = stringResource(R.string.track_list_retry),
                        style = MultiplayerTheme.typography.label,
                        color = MultiplayerTheme.colors.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackListFeedbackScreen(
    title: String,
    headline: String?,
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MultiplayerTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colors.backgroundGradient.start,
                        colors.backgroundGradient.end,
                    ),
                ),
            ),
    ) {
        MultiplayerBrandBackgroundDecor(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = MultiplayerTheme.spacing.lg,
                    vertical = MultiplayerTheme.spacing.xl,
                ),
        ) {
            TrackListHeader(
                title = title,
                subtitle = null,
                onBack = onBack,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                MultiplayerSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MultiplayerTheme.radius.xLarge),
                    tonalElevation = MultiplayerTheme.elevation.level2,
                    shadowElevation = MultiplayerTheme.elevation.level1,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = MultiplayerTheme.spacing.xl,
                            vertical = MultiplayerTheme.spacing.xxxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MultiplayerTheme.spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        headline?.let { value ->
                            MultiplayerText(
                                text = value,
                                style = MultiplayerTheme.typography.title,
                                color = colors.textPrimary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        MultiplayerText(
                            text = message,
                            style = MultiplayerTheme.typography.body,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackListStatus?.toMessage(): String {
    return when (this) {
        TrackListStatus.Empty -> stringResource(R.string.track_list_empty_message)
        TrackListStatus.PrivateLibrary -> stringResource(R.string.track_list_private_library_message)
        TrackListStatus.GenericError,
        null,
        -> stringResource(R.string.track_list_load_error_message)
    }
}

private enum class DevicePermissionUiState {
    Requesting,
    Granted,
    Denied,
}
