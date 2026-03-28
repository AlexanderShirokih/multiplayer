@file:Suppress("MagicNumber", "TooManyFunctions")

package com.mplayeraudio.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mplayeraudio.core.ui.components.MultiplayerCardSurfaceStyle
import com.mplayeraudio.core.ui.preview.MultiplayerPreview
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

private val CompactPrimaryArtworkSize = 44.dp
private val FeaturedPrimaryArtworkSize = 58.dp
private val CompactSecondaryArtworkSize = 22.dp
private val FeaturedSecondaryArtworkSize = 28.dp
private val PatternBorderWidth = 1.dp
private val CompactAccentSize = 12.dp
private val FeaturedAccentSize = 14.dp

@Composable
fun LibraryPlaylistArtwork(
    artwork: PlaylistCardArtwork,
    style: MultiplayerCardSurfaceStyle,
    artworkSeed: Int,
    isFeatured: Boolean,
    modifier: Modifier = Modifier,
) {
    when (artwork) {
        PlaylistCardArtwork.Default -> {
            GeneratedPlaylistArtwork(
                spec = generatedArtworkSpec(
                    style = style,
                    seed = artworkSeed,
                    isFeatured = isFeatured,
                ),
                modifier = modifier,
            )
        }

        PlaylistCardArtwork.Favourites -> {
            Image(
                painter = painterResource(id = R.drawable.ic_library_card_favourites),
                contentDescription = null,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun GeneratedPlaylistArtwork(
    spec: GeneratedPlaylistArtworkSpec,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = spec.anchor,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(spec.cornerRadius))
                .background(spec.palette.backdrop),
        )

        when (spec.pattern) {
            PlaylistArtworkPattern.Orbits -> OrbitPattern(spec = spec)
            PlaylistArtworkPattern.Bars -> BarsPattern(spec = spec)
            PlaylistArtworkPattern.Prism -> PrismPattern(spec = spec)
            PlaylistArtworkPattern.Mosaic -> MosaicPattern(spec = spec)
            PlaylistArtworkPattern.Beacon -> BeaconPattern(spec = spec)
            PlaylistArtworkPattern.Frame -> FramePattern(spec = spec)
        }
    }
}

@Composable
private fun OrbitPattern(spec: GeneratedPlaylistArtworkSpec) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(spec.primarySize)
                .clip(CircleShape)
                .border(
                    width = PatternBorderWidth,
                    color = spec.palette.primary.copy(alpha = 0.52f),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(spec.primarySize * 0.68f)
                .clip(CircleShape)
                .border(
                    width = PatternBorderWidth,
                    color = spec.palette.secondary.copy(alpha = 0.62f),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(spec.accentAlignment)
                .size(spec.accentSize)
                .clip(CircleShape)
                .background(spec.palette.highlight),
        )
    }
}

@Composable
private fun BarsPattern(spec: GeneratedPlaylistArtworkSpec) {
    val barShape = RoundedCornerShape(spec.secondarySize / 2)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-spec.secondaryOffset), y = (-spec.secondaryOffset / 2))
                .fillMaxWidth(if (spec.isFeatured) 0.72f else 0.82f)
                .height(spec.secondarySize)
                .clip(barShape)
                .background(spec.palette.primary.copy(alpha = 0.34f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(if (spec.isFeatured) 0.56f else 0.64f)
                .height(spec.secondarySize)
                .clip(barShape)
                .background(spec.palette.secondary.copy(alpha = 0.56f)),
        )
        Box(
            modifier = Modifier
                .offset(x = spec.secondaryOffset, y = spec.secondaryOffset / 2)
                .fillMaxWidth(if (spec.isFeatured) 0.36f else 0.42f)
                .height(spec.secondarySize)
                .clip(barShape)
                .background(spec.palette.highlight),
        )
    }
}

@Composable
private fun PrismPattern(spec: GeneratedPlaylistArtworkSpec) {
    val shape = RoundedCornerShape(spec.cornerRadius / 1.5f)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(spec.primarySize)
                .graphicsLayer { rotationZ = spec.rotation }
                .clip(shape)
                .background(spec.palette.primary.copy(alpha = 0.22f)),
        )
        Box(
            modifier = Modifier
                .size(spec.primarySize * 0.72f)
                .graphicsLayer { rotationZ = -spec.rotation * 0.7f }
                .clip(shape)
                .background(spec.palette.secondary.copy(alpha = 0.48f)),
        )
        Box(
            modifier = Modifier
                .size(spec.secondarySize)
                .clip(CircleShape)
                .background(spec.palette.highlight),
        )
    }
}

@Composable
private fun MosaicPattern(spec: GeneratedPlaylistArtworkSpec) {
    val tileShape = RoundedCornerShape(spec.cornerRadius / 2)
    val tileSize = spec.secondarySize * 0.92f

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MosaicTile(
            size = tileSize,
            color = spec.palette.primary.copy(alpha = 0.32f),
            x = -spec.secondaryOffset,
            y = -spec.secondaryOffset,
            shape = tileShape,
        )
        MosaicTile(
            size = tileSize,
            color = spec.palette.secondary.copy(alpha = 0.58f),
            x = spec.secondaryOffset,
            y = -spec.secondaryOffset / 2,
            shape = tileShape,
        )
        MosaicTile(
            size = tileSize,
            color = spec.palette.secondary.copy(alpha = 0.42f),
            x = -spec.secondaryOffset / 2,
            y = spec.secondaryOffset,
            shape = tileShape,
        )
        MosaicTile(
            size = tileSize,
            color = spec.palette.highlight,
            x = spec.secondaryOffset / 2,
            y = spec.secondaryOffset / 2,
            shape = tileShape,
        )
    }
}

@Composable
private fun MosaicTile(
    size: Dp,
    color: Color,
    x: Dp,
    y: Dp,
    shape: RoundedCornerShape,
) {
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(size)
            .clip(shape)
            .background(color),
    )
}

@Composable
private fun BeaconPattern(spec: GeneratedPlaylistArtworkSpec) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(if (spec.isFeatured) 0.82f else 0.78f)
                .fillMaxWidth(if (spec.isFeatured) 0.22f else 0.26f)
                .clip(RoundedCornerShape(spec.cornerRadius))
                .background(spec.palette.primary.copy(alpha = 0.26f)),
        )
        Box(
            modifier = Modifier
                .size(spec.primarySize)
                .clip(CircleShape)
                .border(
                    width = PatternBorderWidth,
                    color = spec.palette.secondary.copy(alpha = 0.48f),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(spec.secondarySize)
                .clip(CircleShape)
                .background(spec.palette.highlight),
        )
    }
}

@Composable
private fun FramePattern(spec: GeneratedPlaylistArtworkSpec) {
    val outerShape = RoundedCornerShape(spec.cornerRadius)
    val innerShape = RoundedCornerShape(spec.cornerRadius / 1.5f)
    val pillShape = RoundedCornerShape(spec.secondarySize / 2)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(spec.primarySize)
                .clip(outerShape)
                .border(
                    width = PatternBorderWidth,
                    color = spec.palette.primary.copy(alpha = 0.50f),
                    shape = outerShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(spec.primarySize * 0.66f)
                .clip(innerShape)
                .border(
                    width = PatternBorderWidth,
                    color = spec.palette.secondary.copy(alpha = 0.60f),
                    shape = innerShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(spec.accentAlignment)
                .size(width = spec.primarySize * 0.38f, height = spec.accentSize)
                .clip(pillShape)
                .background(spec.palette.highlight),
        )
    }
}

@Composable
private fun generatedArtworkSpec(
    style: MultiplayerCardSurfaceStyle,
    seed: Int,
    isFeatured: Boolean,
): GeneratedPlaylistArtworkSpec {
    val normalizedSeed = seed and Int.MAX_VALUE
    val pattern = PlaylistArtworkPattern.entries[normalizedSeed % PlaylistArtworkPattern.entries.size]
    val alignment = ArtworkAnchors[normalizedSeed / 7 % ArtworkAnchors.size]
    val accentAlignment = AccentAlignments[normalizedSeed / 13 % AccentAlignments.size]
    val primarySize = if (isFeatured) {
        FeaturedPrimaryArtworkSize
    } else {
        CompactPrimaryArtworkSize
    }
    val secondarySize = if (isFeatured) {
        FeaturedSecondaryArtworkSize
    } else {
        CompactSecondaryArtworkSize
    }

    return GeneratedPlaylistArtworkSpec(
        pattern = pattern,
        anchor = alignment,
        accentAlignment = accentAlignment,
        palette = artworkPalette(style),
        primarySize = primarySize,
        secondarySize = secondarySize,
        secondaryOffset = secondarySize / 2,
        accentSize = if (isFeatured) FeaturedAccentSize else CompactAccentSize,
        offsetX = seededOffset(normalizedSeed, 0, isFeatured),
        offsetY = seededOffset(normalizedSeed, 1, isFeatured),
        rotation = seededRotation(normalizedSeed),
        cornerRadius = if (isFeatured) 24.dp else 18.dp,
        isFeatured = isFeatured,
    )
}

private fun seededOffset(
    seed: Int,
    salt: Int,
    isFeatured: Boolean,
): Dp {
    val step = if (isFeatured) 4.dp else 3.dp
    val spread = if (isFeatured) 4 else 3
    val magnitude = ((seed / (salt + 3)) % (spread * 2 + 1)) - spread

    return step * magnitude
}

private fun seededRotation(seed: Int): Float {
    val step = (seed % 5) - 2
    return step * 12f
}

@Composable
private fun artworkPalette(
    style: MultiplayerCardSurfaceStyle,
): GeneratedArtworkPalette {
    val colors = MultiplayerTheme.colors

    return when (style) {
        MultiplayerCardSurfaceStyle.Surface2 -> GeneratedArtworkPalette(
            backdrop = colors.brandVisualPrimary.copy(alpha = 0.10f),
            primary = colors.brandVisualPrimary,
            secondary = colors.brandVisualPrimaryContainer,
            highlight = colors.brandVisualSecondary,
        )

        MultiplayerCardSurfaceStyle.Surface3 -> GeneratedArtworkPalette(
            backdrop = colors.brandVisualTertiaryMuted.copy(alpha = 0.18f),
            primary = colors.brandVisualTertiary,
            secondary = colors.brandVisualPrimary.copy(alpha = 0.86f),
            highlight = colors.brandVisualPrimaryContainer,
        )

        MultiplayerCardSurfaceStyle.Surface4 -> GeneratedArtworkPalette(
            backdrop = colors.brandVisualSecondaryMuted.copy(alpha = 0.18f),
            primary = colors.brandVisualSecondary,
            secondary = colors.brandVisualPrimary.copy(alpha = 0.82f),
            highlight = colors.brandVisualSecondary,
        )
    }
}

private data class GeneratedPlaylistArtworkSpec(
    val pattern: PlaylistArtworkPattern,
    val anchor: Alignment,
    val accentAlignment: Alignment,
    val palette: GeneratedArtworkPalette,
    val primarySize: Dp,
    val secondarySize: Dp,
    val secondaryOffset: Dp,
    val accentSize: Dp,
    val offsetX: Dp,
    val offsetY: Dp,
    val rotation: Float,
    val cornerRadius: Dp,
    val isFeatured: Boolean,
)

private data class GeneratedArtworkPalette(
    val backdrop: Color,
    val primary: Color,
    val secondary: Color,
    val highlight: Color,
)

private enum class PlaylistArtworkPattern {
    Orbits,
    Bars,
    Prism,
    Mosaic,
    Beacon,
    Frame,
}

private val ArtworkAnchors = listOf(
    Alignment.Center,
    Alignment.TopEnd,
    Alignment.BottomStart,
    Alignment.CenterEnd,
    Alignment.TopCenter,
)

private val AccentAlignments = listOf(
    Alignment.TopEnd,
    Alignment.BottomStart,
    Alignment.BottomEnd,
    Alignment.CenterEnd,
    Alignment.TopCenter,
)

@Preview(showBackground = true, name = "Artwork Dark")
@Composable
private fun LibraryPlaylistArtworkDarkPreview() {
    MultiplayerPreview(darkTheme = true, contentAlignment = Alignment.Center) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LibraryPlaylistArtwork(
                    artwork = PlaylistCardArtwork.Default,
                    style = MultiplayerCardSurfaceStyle.Surface2,
                    artworkSeed = 10,
                    isFeatured = false,
                    modifier = Modifier.size(64.dp),
                )
                LibraryPlaylistArtwork(
                    artwork = PlaylistCardArtwork.Default,
                    style = MultiplayerCardSurfaceStyle.Surface3,
                    artworkSeed = 20,
                    isFeatured = false,
                    modifier = Modifier.size(64.dp),
                )
                LibraryPlaylistArtwork(
                    artwork = PlaylistCardArtwork.Default,
                    style = MultiplayerCardSurfaceStyle.Surface4,
                    artworkSeed = 30,
                    isFeatured = false,
                    modifier = Modifier.size(64.dp),
                )
            }
            LibraryPlaylistArtwork(
                artwork = PlaylistCardArtwork.Default,
                style = MultiplayerCardSurfaceStyle.Surface3,
                artworkSeed = 99,
                isFeatured = true,
                modifier = Modifier.size(92.dp),
            )
        }
    }
}
