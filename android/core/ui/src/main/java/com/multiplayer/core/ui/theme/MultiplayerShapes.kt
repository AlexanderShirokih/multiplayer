package com.multiplayer.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import com.multiplayer.core.ui.tokens.MultiplayerRadius

internal fun multiplayerShapes(radius: MultiplayerRadius): Shapes = Shapes(
    small = RoundedCornerShape(radius.small),
    medium = RoundedCornerShape(radius.medium),
    large = RoundedCornerShape(radius.large),
)
