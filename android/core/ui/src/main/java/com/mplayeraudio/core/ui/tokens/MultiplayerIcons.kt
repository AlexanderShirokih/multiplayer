package com.mplayeraudio.core.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class MultiplayerIcons(
    val appLogo: ImageVector? = null,
    val play: ImageVector? = null,
    val pause: ImageVector? = null,
    val next: ImageVector? = null,
    val previous: ImageVector? = null,
)
