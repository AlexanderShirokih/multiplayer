package com.mplayeraudio.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import com.mplayeraudio.core.ui.tokens.MultiplayerRadius

internal fun multiplayerShapes(radius: MultiplayerRadius): Shapes = Shapes(
    small = RoundedCornerShape(radius.small),
    medium = RoundedCornerShape(radius.medium),
    large = RoundedCornerShape(radius.large),
)
