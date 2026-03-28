@file:Suppress("TooManyFunctions")

package com.mplayeraudio.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.ui.components.MultiplayerBrandBackgroundDecor
import com.mplayeraudio.core.ui.components.MultiplayerCardSurface
import com.mplayeraudio.core.ui.components.MultiplayerCardSurfaceStyle
import com.mplayeraudio.core.ui.components.MultiplayerText
import com.mplayeraudio.core.ui.preview.MultiplayerPreview
import com.mplayeraudio.core.ui.theme.MultiplayerDesignSystem
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

private val LibraryHorizontalPadding = 20.dp
private val LibraryColumnSpacing = 12.dp
private val CompactCardMinHeight = 164.dp
private val FeaturedCardMinHeight = 132.dp
private val CompactArtworkSize = 64.dp
private val FeaturedArtworkSize = 92.dp
private val HeroArtworkSize = 108.dp

@Composable
fun MusicLibraryScreen(
    state: MusicLibraryState,
    onPlaylistClick: (PlaylistId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MultiplayerTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        MultiplayerBrandBackgroundDecor(modifier = Modifier.fillMaxSize())

        if (state.isLoading && state.content == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = colors.accent,
            )
        } else {
            state.content?.let { content ->
                LibraryContent(
                    content = content,
                    onPlaylistClick = onPlaylistClick,
                )
            }
        }
    }
}

@Composable
private fun LibraryContent(
    content: MusicLibraryContent,
    onPlaylistClick: (PlaylistId) -> Unit,
) {
    val spacing = MultiplayerTheme.spacing

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(LibraryColumnSpacing),
        verticalItemSpacing = spacing.md,
        contentPadding = PaddingValues(
            start = LibraryHorizontalPadding,
            end = LibraryHorizontalPadding,
            top = spacing.xl,
            bottom = spacing.xxxl,
        ),
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            LibraryHeader()
        }

        items(
            items = content.cards,
            key = MusicLibraryCard::key,
            span = { card ->
                when (card) {
                    is MusicLibraryCard.Playlist -> {
                        if (card.layout == PlaylistCardLayout.Featured) {
                            StaggeredGridItemSpan.FullLine
                        } else {
                            StaggeredGridItemSpan.SingleLane
                        }
                    }
                }
            },
        ) { card ->
            when (card) {
                is MusicLibraryCard.Playlist -> {
                    LibraryPlaylistCard(
                        playlist = card,
                        onClick = { onPlaylistClick(card.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader() {
    val spacing = MultiplayerTheme.spacing
    val typography = MultiplayerTheme.typography
    val colors = MultiplayerTheme.colors

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        MultiplayerText(
            text = stringResource(R.string.library_title),
            style = typography.pageTitle,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(spacing.xxs))
        MultiplayerText(
            text = stringResource(R.string.library_subtitle),
            style = typography.pageSubtitle,
            color = colors.textSurfaceSecondary,
        )
        Spacer(modifier = Modifier.height(spacing.xs))
    }
}

@Composable
private fun LibraryPlaylistCard(
    playlist: MusicLibraryCard.Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFeatured = playlist.layout == PlaylistCardLayout.Featured
    val spacing = MultiplayerTheme.spacing
    val typography = MultiplayerTheme.typography
    val colors = MultiplayerTheme.colors

    MultiplayerCardSurface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isFeatured) FeaturedCardMinHeight else CompactCardMinHeight),
        style = playlist.style,
        contentPadding = PaddingValues(
            horizontal = if (isFeatured) spacing.lg else spacing.md,
            vertical = if (isFeatured) spacing.lg else spacing.md,
        ),
        onClick = onClick,
    ) {
        if (isFeatured) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    MultiplayerText(
                        text = playlist.title,
                        style = typography.cardTitleLarge,
                        color = colors.textSurfacePrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MultiplayerText(
                        text = stringResource(R.string.library_tracks_count, playlist.trackCount),
                        style = typography.cardMeta,
                        color = colors.textSurfaceSecondary,
                    )
                }

                LibraryPlaylistArtwork(
                    artwork = playlist.artwork,
                    style = playlist.style,
                    artworkSeed = playlist.artworkSeed,
                    isFeatured = true,
                    modifier = Modifier.size(FeaturedArtworkSize),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    LibraryPlaylistArtwork(
                        artwork = playlist.artwork,
                        style = playlist.style,
                        artworkSeed = playlist.artworkSeed,
                        isFeatured = false,
                        modifier = Modifier.size(CompactArtworkSize),
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    MultiplayerText(
                        text = playlist.title,
                        style = typography.cardTitleCompact,
                        color = colors.textSurfacePrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MultiplayerText(
                        text = stringResource(R.string.library_tracks_count, playlist.trackCount),
                        style = typography.cardMeta,
                        color = colors.textSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun previewPlaylistId(kind: Long): PlaylistId {
    return PlaylistId(
        ownerId = ProviderUserId("preview"),
        kind = PlaylistKind(kind),
    )
}

@Suppress("MagicNumber")
private fun previewLibraryContent(): MusicLibraryContent {
    return MusicLibraryContent(
        cards = listOf(
            MusicLibraryCard.Playlist(
                id = previewPlaylistId(1),
                title = "Ночной код",
                trackCount = 248,
                layout = PlaylistCardLayout.Compact,
                style = MultiplayerCardSurfaceStyle.Surface2,
                artwork = PlaylistCardArtwork.Default,
                artworkSeed = 101,
            ),
            MusicLibraryCard.Playlist(
                id = previewPlaylistId(2),
                title = "Любимые",
                trackCount = 248,
                layout = PlaylistCardLayout.Compact,
                style = MultiplayerCardSurfaceStyle.Surface4,
                artwork = PlaylistCardArtwork.Favourites,
                artworkSeed = 202,
            ),
            MusicLibraryCard.Playlist(
                id = previewPlaylistId(3),
                title = "Маршрут дня",
                trackCount = 64,
                layout = PlaylistCardLayout.Featured,
                style = MultiplayerCardSurfaceStyle.Surface3,
                artwork = PlaylistCardArtwork.Default,
                artworkSeed = 303,
            ),
        ),
    )
}

@Preview(showBackground = true)
@Composable
@Suppress("MagicNumber")
private fun MusicLibraryScreenPreview() {
    MultiplayerDesignSystem(darkTheme = false) {
        MusicLibraryScreen(
            state = MusicLibraryState(
                isLoading = false,
                content = previewLibraryContent(),
            ),
            onPlaylistClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Music Library Dark")
@Composable
private fun MusicLibraryScreenDarkPreview() {
    MultiplayerDesignSystem(darkTheme = true) {
        MusicLibraryScreen(
            state = MusicLibraryState(
                isLoading = false,
                content = previewLibraryContent(),
            ),
            onPlaylistClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Playlist Compact Dark")
@Composable
@Suppress("MagicNumber")
private fun LibraryPlaylistCardCompactDarkPreview() {
    MultiplayerPreview(darkTheme = true, contentAlignment = Alignment.Center) {
        LibraryPlaylistCard(
            playlist = MusicLibraryCard.Playlist(
                id = previewPlaylistId(11),
                title = "После полуночи",
                trackCount = 87,
                layout = PlaylistCardLayout.Compact,
                style = MultiplayerCardSurfaceStyle.Surface2,
                artwork = PlaylistCardArtwork.Default,
                artworkSeed = 111,
            ),
            onClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Playlist Featured Dark")
@Composable
@Suppress("MagicNumber")
private fun LibraryPlaylistCardFeaturedDarkPreview() {
    MultiplayerPreview(darkTheme = true, contentAlignment = Alignment.Center) {
        LibraryPlaylistCard(
            playlist = MusicLibraryCard.Playlist(
                id = previewPlaylistId(12),
                title = "Сигналы города",
                trackCount = 152,
                layout = PlaylistCardLayout.Featured,
                style = MultiplayerCardSurfaceStyle.Surface3,
                artwork = PlaylistCardArtwork.Default,
                artworkSeed = 222,
            ),
            onClick = {},
        )
    }
}
