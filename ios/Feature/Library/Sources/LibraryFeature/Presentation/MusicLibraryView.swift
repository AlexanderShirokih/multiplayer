import CoreDomain
import CoreUI
import SwiftUI

private let libraryHorizontalPadding: CGFloat = 20
private let libraryColumnSpacing: CGFloat = 12
private let compactCardMinHeight: CGFloat = 164
private let featuredCardMinHeight: CGFloat = 132
private let compactArtworkSize: CGFloat = 64
private let featuredArtworkSize: CGFloat = 92

public struct MusicLibraryView: View {
    @State private var viewModel: MusicLibraryViewModel
    @State private var isResetAuthorizationConfirmationPresented = false

    private let trackListViewModelFactory: (MusicLibraryDestination) -> TrackListViewModel
    private let onResetAuthorization: (@Sendable () async -> Void)?

    public init(
        viewModel: MusicLibraryViewModel,
        trackListViewModelFactory: @escaping (MusicLibraryDestination) -> TrackListViewModel,
        onResetAuthorization: (@Sendable () async -> Void)? = nil
    ) {
        _viewModel = State(initialValue: viewModel)
        self.trackListViewModelFactory = trackListViewModelFactory
        self.onResetAuthorization = onResetAuthorization
    }

    public var body: some View {
        NavigationStack {
            MusicLibraryContentView(
                state: viewModel.state,
                onAction: viewModel.send
            )
            .navigationDestination(item: selectedPlaylistBinding) { destination in
                TrackListView(viewModel: trackListViewModelFactory(destination))
            }
#if DEBUG
            .toolbar {
                if onResetAuthorization != nil {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Сбросить auth") {
                            isResetAuthorizationConfirmationPresented = true
                        }
                    }
                }
            }
            .confirmationDialog(
                "Сбросить авторизацию Яндекс Музыки?",
                isPresented: $isResetAuthorizationConfirmationPresented,
                titleVisibility: .visible
            ) {
                Button("Сбросить", role: .destructive) {
                    Task {
                        await onResetAuthorization?()
                    }
                }

                Button("Отмена", role: .cancel) {}
            } message: {
                Text("Приложение очистит локальную сессию и вернёт вас на экран входа.")
            }
#endif
        }
        .task {
            viewModel.start()
        }
    }

    private var selectedPlaylistBinding: Binding<MusicLibraryDestination?> {
        Binding(
            get: { viewModel.state.selectedPlaylist },
            set: { newValue in
                if newValue == nil {
                    viewModel.send(.dismissPlaylistDetail)
                }
            }
        )
    }
}

struct MusicLibraryContentView: View {
    @Environment(\.multiplayerTheme) private var theme

    let state: MusicLibraryState
    let onAction: (MusicLibraryAction) -> Void

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                MultiplayerBrandBackground()
                LibraryDecorativeCurve()

                if state.isLoading && state.content == nil {
                    ProgressView()
                        .tint(theme.colors.accent)
                } else {
                    ScrollView(showsIndicators: false) {
                        LibraryStaggeredGrid(
                            columnSpacing: libraryColumnSpacing,
                            rowSpacing: theme.spacing.md
                        ) {
                            LibraryHeaderView()
                                .libraryGridSpan(.fullWidth)

                            ForEach(state.content?.cards ?? []) { card in
                                cardView(for: card)
                                    .libraryGridSpan(card.span)
                            }
                        }
                        .frame(width: max(proxy.size.width - (libraryHorizontalPadding * 2), 0))
                        .padding(.horizontal, libraryHorizontalPadding)
                        .padding(.top, theme.spacing.xl)
                        .padding(.bottom, theme.spacing.xxxl)
                    }
                    .safeAreaPadding(.top)
                    .safeAreaPadding(.bottom)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ViewBuilder
    private func cardView(for card: MusicLibraryCard) -> some View {
        switch card {
        case let .playlist(playlist):
            LibraryPlaylistCardView(
                playlist: playlist,
                onTap: {
                    onAction(.playlistTapped(playlist.id))
                }
            )
        }
    }
}

private struct LibraryHeaderView: View {
    @Environment(\.multiplayerTheme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            MultiplayerText(
                verbatim: "Моя музыка",
                style: theme.typography.pageTitle,
                color: theme.colors.textPrimary
            )

            Spacer()
                .frame(height: theme.spacing.xxs)

            MultiplayerText(
                verbatim: "Плейлисты, которые всегда под рукой",
                style: theme.typography.secondaryBody,
                color: theme.colors.textSecondary
            )

            Spacer()
                .frame(height: theme.spacing.xs)
        }
    }
}

private struct LibraryPlaylistCardView: View {
    @Environment(\.multiplayerTheme) private var theme

    let playlist: PlaylistCard
    let onTap: () -> Void

    private var isFeatured: Bool {
        playlist.layout == .featured
    }

    var body: some View {
        Button(action: onTap) {
            MultiplayerCardSurface(
                style: playlist.style,
                contentPadding: EdgeInsets(
                    top: isFeatured ? theme.spacing.lg : theme.spacing.md,
                    leading: isFeatured ? theme.spacing.lg : theme.spacing.md,
                    bottom: isFeatured ? theme.spacing.lg : theme.spacing.md,
                    trailing: isFeatured ? theme.spacing.lg : theme.spacing.md
                )
            ) {
                if isFeatured {
                    featuredContent
                } else {
                    compactContent
                }
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: isFeatured ? featuredCardMinHeight : compactCardMinHeight)
        }
        .buttonStyle(.plain)
    }

    private var featuredContent: some View {
        HStack(spacing: theme.spacing.md) {
            VStack(alignment: .leading, spacing: theme.spacing.xxs) {
                MultiplayerText(
                    verbatim: playlist.title,
                    style: theme.typography.title,
                    color: theme.colors.surfaceContentPrimary,
                    lineLimit: 2
                )

                MultiplayerText(
                    verbatim: tracksCountText(for: playlist.trackCount),
                    style: theme.typography.meta,
                    color: theme.colors.surfaceContentSecondary,
                    lineLimit: 1
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            LibraryPlaylistArtworkView(
                artwork: playlist.artwork,
                style: playlist.style,
                artworkSeed: playlist.artworkSeed,
                isFeatured: true
            )
            .frame(width: featuredArtworkSize, height: featuredArtworkSize)
        }
    }

    private var compactContent: some View {
        VStack(alignment: .leading, spacing: theme.spacing.lg) {
            HStack {
                Spacer(minLength: 0)

                LibraryPlaylistArtworkView(
                    artwork: playlist.artwork,
                    style: playlist.style,
                    artworkSeed: playlist.artworkSeed,
                    isFeatured: false
                )
                .frame(width: compactArtworkSize, height: compactArtworkSize)
            }

            VStack(alignment: .leading, spacing: theme.spacing.xxs) {
                MultiplayerText(
                    verbatim: playlist.title,
                    style: theme.typography.compactTitle,
                    color: theme.colors.surfaceContentPrimary,
                    lineLimit: 2
                )

                MultiplayerText(
                    verbatim: tracksCountText(for: playlist.trackCount),
                    style: theme.typography.meta,
                    color: theme.colors.surfaceContentSecondary,
                    lineLimit: 1
                )
            }
        }
    }

    private func tracksCountText(for count: Int) -> String {
        "\(count) \(trackWord(for: count))"
    }

    private func trackWord(for count: Int) -> String {
        let mod10 = count % 10
        let mod100 = count % 100

        if mod10 == 1 && mod100 != 11 {
            return "трек"
        }
        if (2...4).contains(mod10) && !(12...14).contains(mod100) {
            return "трека"
        }
        return "треков"
    }
}

private struct LibraryDecorativeCurve: View {
    @Environment(\.colorScheme) private var colorScheme

    private var palette: MultiplayerColors {
        MultiplayerTheme.default(for: colorScheme).colors
    }

    private var fillColor: Color {
        switch colorScheme {
        case .dark:
            palette.brandVisualPrimaryContainer.opacity(0.16)

        default:
            palette.brandVisualPrimary.opacity(0.12)
        }
    }

    var body: some View {
        GeometryReader { proxy in
            Path { path in
                path.move(to: CGPoint(x: 0, y: proxy.size.height * 0.45))
                path.addCurve(
                    to: CGPoint(x: proxy.size.width * 0.46, y: proxy.size.height * 0.35),
                    control1: CGPoint(x: proxy.size.width * 0.17, y: proxy.size.height * 0.42),
                    control2: CGPoint(x: proxy.size.width * 0.31, y: proxy.size.height * 0.39)
                )
                path.addCurve(
                    to: CGPoint(x: proxy.size.width, y: proxy.size.height * 0.22),
                    control1: CGPoint(x: proxy.size.width * 0.61, y: proxy.size.height * 0.31),
                    control2: CGPoint(x: proxy.size.width * 0.77, y: proxy.size.height * 0.24)
                )
                path.addLine(to: CGPoint(x: proxy.size.width, y: proxy.size.height))
                path.addLine(to: CGPoint(x: 0, y: proxy.size.height))
                path.closeSubpath()
            }
            .fill(fillColor)
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
    }
}

#Preview("Light") {
    MultiplayerDesignSystem(colorScheme: .light) {
        MusicLibraryView(
            viewModel: MusicLibraryPreviewFactory.makeViewModel(),
            trackListViewModelFactory: MusicLibraryPreviewFactory.makeTrackListViewModel
        )
    }
}

#Preview("Dark") {
    MultiplayerDesignSystem(colorScheme: .dark) {
        MusicLibraryView(
            viewModel: MusicLibraryPreviewFactory.makeViewModel(),
            trackListViewModelFactory: MusicLibraryPreviewFactory.makeTrackListViewModel
        )
    }
}
