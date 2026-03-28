import CoreUI
import SwiftUI

private let trackListContentMaxWidth: CGFloat = 420

public struct TrackListView: View {
    private let state: TrackListState
    private let onNowPlayingAction: (NowPlayingStripAction) -> Void
    private let onTrackTap: (Int) -> Void

    public init(
        state: TrackListState,
        onNowPlayingAction: @escaping (NowPlayingStripAction) -> Void = { _ in },
        onTrackTap: @escaping (Int) -> Void = { _ in }
    ) {
        self.state = state
        self.onNowPlayingAction = onNowPlayingAction
        self.onTrackTap = onTrackTap
    }

    public var body: some View {
        TrackListContentView(
            state: state,
            onNowPlayingAction: onNowPlayingAction,
            onTrackTap: onTrackTap
        )
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
    }
}

struct TrackListContentView: View {
    @Environment(\.multiplayerTheme) private var theme

    let state: TrackListState
    let onNowPlayingAction: (NowPlayingStripAction) -> Void
    let onTrackTap: (Int) -> Void

    var body: some View {
        GeometryReader { proxy in
            let horizontalPadding = theme.spacing.lg
            let contentWidth = min(
                max(proxy.size.width - (horizontalPadding * 2), 0),
                trackListContentMaxWidth
            )

            ZStack {
                MultiplayerBrandBackground()

                VStack(alignment: .leading, spacing: theme.spacing.xl) {
                    TrackListHeaderView(
                        title: state.title,
                        subtitle: state.subtitle
                    )

                    NowPlayingStrip(
                        state: state.nowPlaying,
                        onAction: onNowPlayingAction,
                        showBorder: false
                    )

                    TrackListPanelView(
                        tracks: state.tracks,
                        onTrackTap: onTrackTap
                    )
                    .frame(maxHeight: .infinity, alignment: .top)
                }
                .frame(width: contentWidth)
                .padding(.horizontal, horizontalPadding)
                .padding(.top, theme.spacing.md)
                .padding(.bottom, theme.spacing.md)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
        }
    }
}

private struct TrackListHeaderView: View {
    @Environment(\.multiplayerTheme) private var theme

    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            MultiplayerText(
                verbatim: title,
                style: theme.typography.pageTitle,
                color: theme.colors.textPrimary
            )

            Spacer()
                .frame(height: theme.spacing.sm)

            MultiplayerText(
                verbatim: subtitle,
                style: theme.typography.label,
                color: theme.colors.textSecondary
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct TrackListPanelView: View {
    @Environment(\.multiplayerTheme) private var theme

    let tracks: [MultiplayerTrackListItemState]
    let onTrackTap: (Int) -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: theme.radius.large, style: .continuous)

        ScrollView(showsIndicators: false) {
            LazyVStack(spacing: theme.spacing.md) {
                ForEach(Array(tracks.enumerated()), id: \.offset) { index, track in
                    MultiplayerTrackListItem(
                        state: track,
                        onClick: {
                            onTrackTap(index)
                        }
                    )
                }
            }
            .padding(.horizontal, theme.spacing.lg)
            .padding(.top, theme.spacing.xl)
            .padding(.bottom, theme.spacing.xl)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background {
            shape
                .fill(theme.colors.surface2Gradient.linearGradient())
        }
        .overlay {
            shape
                .stroke(theme.colors.borderSubtle.opacity(0.42), lineWidth: 1)
        }
        .clipShape(shape)
    }
}

#Preview("Track List - Light") {
    MultiplayerDesignSystem(colorScheme: .light) {
        NavigationStack {
            TrackListView(state: TrackListPreviewFactory.makeState())
        }
    }
}

#Preview("Track List - Dark") {
    MultiplayerDesignSystem(colorScheme: .dark) {
        NavigationStack {
            TrackListView(state: TrackListPreviewFactory.makeState())
        }
    }
}
