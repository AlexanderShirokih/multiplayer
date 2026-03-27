package com.multiplayer.core.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class MultiplayerRadius(
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 20.dp,
    val xLarge: Dp = 28.dp,
    val pill: Dp = 999.dp,
)
