import CoreUI
import SwiftUI

private let compactPrimaryArtworkSize: CGFloat = 44
private let featuredPrimaryArtworkSize: CGFloat = 58
private let compactSecondaryArtworkSize: CGFloat = 22
private let featuredSecondaryArtworkSize: CGFloat = 28

struct LibraryPlaylistArtworkView: View {
    @Environment(\.multiplayerTheme) private var theme

    let artwork: PlaylistCardArtwork
    let style: MultiplayerCardSurfaceStyle
    let artworkSeed: Int
    let isFeatured: Bool

    var body: some View {
        switch artwork {
        case .default:
            GeneratedPlaylistArtworkView(
                spec: GeneratedPlaylistArtworkSpec(
                    seed: artworkSeed,
                    style: style,
                    theme: theme,
                    isFeatured: isFeatured
                )
            )

        case .favourites:
            FavouritePlaylistArtworkView(isFeatured: isFeatured)
        }
    }
}

private struct FavouritePlaylistArtworkView: View {
    @Environment(\.multiplayerTheme) private var theme

    let isFeatured: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: isFeatured ? 24 : 20, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [
                            theme.colors.accent,
                            theme.colors.brandVisualSecondary
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            RoundedRectangle(cornerRadius: isFeatured ? 19 : 15, style: .continuous)
                .fill(theme.colors.textInverse.opacity(0.12))
                .padding(isFeatured ? 14 : 10)

            Image(systemName: "heart.fill")
                .font(.system(size: isFeatured ? 24 : 18, weight: .semibold))
                .foregroundStyle(theme.colors.textInverse)

            Capsule()
                .fill(theme.colors.textInverse.opacity(0.76))
                .frame(width: isFeatured ? 42 : 30, height: 4)
                .offset(y: isFeatured ? 24 : 18)
        }
    }
}

private struct GeneratedPlaylistArtworkView: View {
    let spec: GeneratedPlaylistArtworkSpec

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: spec.cornerRadius, style: .continuous)
                .fill(spec.palette.backdrop)

            switch spec.pattern {
            case .orbit:
                OrbitPatternView(spec: spec)

            case .bars:
                BarsPatternView(spec: spec)

            case .prism:
                PrismPatternView(spec: spec)

            case .mosaic:
                MosaicPatternView(spec: spec)
            }
        }
        .compositingGroup()
    }
}

private struct OrbitPatternView: View {
    let spec: GeneratedPlaylistArtworkSpec

    var body: some View {
        ZStack {
            Circle()
                .stroke(spec.palette.primary.opacity(0.56), lineWidth: 1)
                .frame(width: spec.primarySize, height: spec.primarySize)

            Circle()
                .stroke(spec.palette.secondary.opacity(0.7), lineWidth: 1)
                .frame(width: spec.primarySize * 0.68, height: spec.primarySize * 0.68)

            Circle()
                .fill(spec.palette.highlight)
                .frame(width: spec.accentSize, height: spec.accentSize)
                .offset(x: spec.accentOffset.width, y: spec.accentOffset.height)
        }
    }
}

private struct BarsPatternView: View {
    let spec: GeneratedPlaylistArtworkSpec

    var body: some View {
        ZStack {
            Capsule()
                .fill(spec.palette.primary.opacity(0.34))
                .frame(width: spec.primarySize * 1.1, height: spec.secondarySize)
                .offset(x: -spec.secondaryOffset, y: -spec.secondaryOffset * 0.45)

            Capsule()
                .fill(spec.palette.secondary.opacity(0.56))
                .frame(width: spec.primarySize * 0.82, height: spec.secondarySize)

            Capsule()
                .fill(spec.palette.highlight)
                .frame(width: spec.primarySize * 0.58, height: spec.secondarySize)
                .offset(x: spec.secondaryOffset, y: spec.secondaryOffset * 0.4)
        }
    }
}

private struct PrismPatternView: View {
    let spec: GeneratedPlaylistArtworkSpec

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: spec.cornerRadius * 0.7, style: .continuous)
                .fill(spec.palette.primary.opacity(0.28))
                .frame(width: spec.primarySize, height: spec.primarySize)
                .rotationEffect(.degrees(spec.rotation))

            RoundedRectangle(cornerRadius: spec.cornerRadius * 0.55, style: .continuous)
                .fill(spec.palette.secondary.opacity(0.48))
                .frame(width: spec.primarySize * 0.72, height: spec.primarySize * 0.72)
                .rotationEffect(.degrees(-spec.rotation * 0.7))

            Circle()
                .fill(spec.palette.highlight)
                .frame(width: spec.secondarySize, height: spec.secondarySize)
        }
    }
}

private struct MosaicPatternView: View {
    let spec: GeneratedPlaylistArtworkSpec

    var body: some View {
        let tileSize = spec.secondarySize * 0.92

        ZStack {
            mosaicTile(
                tileSize,
                color: spec.palette.primary.opacity(0.32),
                xOffset: -spec.secondaryOffset,
                yOffset: -spec.secondaryOffset
            )
            mosaicTile(
                tileSize,
                color: spec.palette.secondary.opacity(0.58),
                xOffset: spec.secondaryOffset,
                yOffset: -spec.secondaryOffset * 0.5
            )
            mosaicTile(
                tileSize,
                color: spec.palette.secondary.opacity(0.42),
                xOffset: -spec.secondaryOffset * 0.5,
                yOffset: spec.secondaryOffset
            )
            mosaicTile(
                tileSize,
                color: spec.palette.highlight,
                xOffset: spec.secondaryOffset * 0.5,
                yOffset: spec.secondaryOffset * 0.5
            )
        }
    }

    private func mosaicTile(
        _ size: CGFloat,
        color: Color,
        xOffset: CGFloat,
        yOffset: CGFloat
    ) -> some View {
        RoundedRectangle(cornerRadius: spec.cornerRadius * 0.45, style: .continuous)
            .fill(color)
            .frame(width: size, height: size)
            .offset(x: xOffset, y: yOffset)
    }
}

private struct GeneratedPlaylistArtworkSpec {
    let palette: PlaylistArtworkPalette
    let pattern: PlaylistArtworkPattern
    let primarySize: CGFloat
    let secondarySize: CGFloat
    let accentSize: CGFloat
    let cornerRadius: CGFloat
    let secondaryOffset: CGFloat
    let accentOffset: CGSize
    let rotation: Double

    init(
        seed: Int,
        style: MultiplayerCardSurfaceStyle,
        theme: MultiplayerTheme,
        isFeatured: Bool
    ) {
        palette = PlaylistArtworkPalette(style: style, theme: theme)
        pattern = PlaylistArtworkPattern(seed: seed)
        primarySize = isFeatured ? featuredPrimaryArtworkSize : compactPrimaryArtworkSize
        secondarySize = isFeatured ? featuredSecondaryArtworkSize : compactSecondaryArtworkSize
        accentSize = isFeatured ? 14 : 12
        cornerRadius = isFeatured ? 24 : 18
        secondaryOffset = isFeatured ? 13 : 10
        accentOffset = CGSize(
            width: CGFloat(seed.isMultiple(of: 2) ? 1 : -1) * secondaryOffset,
            height: CGFloat(seed.isMultiple(of: 3) ? -1 : 1) * secondaryOffset
        )
        rotation = Double((abs(seed) % 7) * 7 + 14)
    }
}

private enum PlaylistArtworkPattern {
    case orbit
    case bars
    case prism
    case mosaic

    init(seed: Int) {
        switch abs(seed) % 4 {
        case 0: self = .orbit

        case 1: self = .bars

        case 2: self = .prism

        default: self = .mosaic
        }
    }
}

private struct PlaylistArtworkPalette {
    let backdrop: Color
    let primary: Color
    let secondary: Color
    let highlight: Color

    init(style: MultiplayerCardSurfaceStyle, theme: MultiplayerTheme) {
        switch style {
        case .surface1:
            backdrop = theme.colors.surfacePrimary.opacity(0.18)
            primary = theme.colors.textPrimary.opacity(0.32)
            secondary = theme.colors.textPrimary.opacity(0.52)
            highlight = theme.colors.surfacePrimary

        case .surface2:
            backdrop = theme.colors.surfacePrimary.opacity(0.28)
            primary = theme.colors.brandVisualPrimary
            secondary = theme.colors.brandVisualPrimaryMuted
            highlight = theme.colors.brandVisualSecondary

        case .surface3:
            backdrop = theme.colors.surfacePrimary.opacity(0.24)
            primary = theme.colors.brandVisualTertiary
            secondary = theme.colors.brandVisualPrimaryMuted
            highlight = theme.colors.brandVisualSecondary

        case .surface4:
            backdrop = theme.colors.brandVisualSecondary.opacity(0.18)
            primary = theme.colors.brandVisualPrimaryMuted
            secondary = theme.colors.brandVisualPrimary
            highlight = theme.colors.brandVisualSecondary
        }
    }
}
