package com.mplayeraudio.feature.library

import androidx.compose.runtime.Immutable
import com.mplayeraudio.core.player.NowPlayingStripState
import com.mplayeraudio.core.ui.model.MultiplayerTrackListItemState

@Immutable
data class TrackListScreenState(
    val title: String,
    val subtitle: String,
    val nowPlaying: NowPlayingStripState,
    val tracks: List<MultiplayerTrackListItemState>,
)
