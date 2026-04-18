import CoreUI
import SwiftUI

private let trackListContentMaxWidth: CGFloat = 420

public struct TrackListView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var viewModel: TrackListViewModel

    public init(viewModel: TrackListViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        Group {
            if !viewModel.trackRows.isEmpty {
                TrackListContentView(
                    title: viewModel.title,
                    subtitle: viewModel.subtitle,
                    nowPlaying: viewModel.nowPlayingStrip,
                    tracks: viewModel.trackRows,
                    onNowPlayingAction: viewModel.onNowPlayingAction,
                    onTrackTap: viewModel.onTrackTap
                )
            } else if viewModel.isLoading {
                TrackListFeedbackView(
                    title: viewModel.title,
                    subtitle: TrackListCopy.trackCountSubtitle(trackCount: 0),
                    message: TrackListCopy.loadingMessage,
                    isLoading: true,
                    actionTitle: nil,
                    onAction: nil
                )
            } else {
                TrackListFeedbackView(
                    title: viewModel.title,
                    subtitle: TrackListCopy.trackCountSubtitle(trackCount: 0),
                    message: viewModel.feedbackMessage,
                    isLoading: false,
                    actionTitle: viewModel.feedbackActionTitle,
                    onAction: viewModel.onFeedbackAction
                )
            }
        }
        .navigationTitle("")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        #endif
        .task {
            viewModel.start()
        }
        .onChange(of: scenePhase) { newPhase in
            guard newPhase == .active else { return }
            viewModel.onAppDidBecomeActive()
        }
        .onDisappear {
            viewModel.stop()
        }
    }
}

struct TrackListContentView: View {
    @Environment(\.multiplayerTheme) private var theme

    let title: String
    let subtitle: String
    let nowPlaying: NowPlayingStripState?
    let tracks: [MultiplayerTrackListItemState]
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
                        title: title,
                        subtitle: subtitle
                    )

                    if let nowPlaying {
                        NowPlayingStrip(
                            state: nowPlaying,
                            onAction: onNowPlayingAction,
                            showBorder: false
                        )
                    }

                    TrackListPanelView(
                        tracks: tracks,
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

private struct TrackListFeedbackView: View {
    @Environment(\.multiplayerTheme) private var theme

    let title: String
    let subtitle: String
    let message: String
    let isLoading: Bool
    let actionTitle: String?
    let onAction: (() -> Void)?

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
                        title: title,
                        subtitle: subtitle
                    )

                    Spacer(minLength: 0)

                    VStack(alignment: .center, spacing: theme.spacing.md) {
                        MultiplayerText(
                            verbatim: message,
                            style: theme.typography.body,
                            color: theme.colors.textSecondary,
                            alignment: .center
                        )

                        if isLoading {
                            ProgressView()
                                .tint(theme.colors.accent)
                        } else if let actionTitle, let onAction {
                            Button(action: onAction) {
                                MultiplayerText(
                                    verbatim: actionTitle,
                                    style: theme.typography.label,
                                    color: theme.colors.accent
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, theme.spacing.xl)
                    .padding(.vertical, theme.spacing.xxxl)
                    .background {
                        RoundedRectangle(cornerRadius: theme.radius.xLarge, style: .continuous)
                            .fill(theme.colors.surface2Gradient.linearGradient())
                    }
                    .overlay {
                        RoundedRectangle(cornerRadius: theme.radius.xLarge, style: .continuous)
                            .stroke(theme.colors.borderSubtle.opacity(0.42), lineWidth: 1)
                    }

                    Spacer(minLength: 0)
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

#Preview("Track List Content - Light") {
    MultiplayerDesignSystem(colorScheme: .light) {
        NavigationStack {
            TrackListContentView(
                title: TrackListPreviewFactory.makeState().title,
                subtitle: TrackListPreviewFactory.makeState().subtitle,
                nowPlaying: TrackListPreviewFactory.makeState().nowPlaying,
                tracks: TrackListPreviewFactory.makeState().tracks,
                onNowPlayingAction: { _ in },
                onTrackTap: { _ in }
            )
        }
    }
}

#Preview("Track List Content - Dark") {
    MultiplayerDesignSystem(colorScheme: .dark) {
        NavigationStack {
            TrackListContentView(
                title: TrackListPreviewFactory.makeState().title,
                subtitle: TrackListPreviewFactory.makeState().subtitle,
                nowPlaying: TrackListPreviewFactory.makeState().nowPlaying,
                tracks: TrackListPreviewFactory.makeState().tracks,
                onNowPlayingAction: { _ in },
                onTrackTap: { _ in }
            )
        }
    }
}
