package com.mplayeraudio.core.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class MultiplayerTrackListItemState(
    val title: String,
    val artist: String,
    val duration: String,
    val trackPosition: Int,
    val isActive: Boolean = false,
)
