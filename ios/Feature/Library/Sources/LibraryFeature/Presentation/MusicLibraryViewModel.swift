import CoreDomain
import CoreUI
import Foundation
import Observation

private let playlistCardStyles: [MultiplayerCardSurfaceStyle] = [
    .surface2,
    .surface3,
    .surface4
]

@Observable
@MainActor
public final class MusicLibraryViewModel {
    public private(set) var state = MusicLibraryState()

    private let observeOwnPlaylists: ObserveOwnPlaylistsUseCase
    private let refreshLibrary: RefreshLibraryUseCase
    private var observationTask: Task<Void, Never>?
    private var refreshTask: Task<Void, Never>?

    public init(
        observeOwnPlaylists: ObserveOwnPlaylistsUseCase,
        refreshLibrary: RefreshLibraryUseCase
    ) {
        self.observeOwnPlaylists = observeOwnPlaylists
        self.refreshLibrary = refreshLibrary
    }

    public func start() {
        guard observationTask == nil else { return }

        observationTask = Task { [weak self] in
            guard let self else { return }

            for await playlists in observeOwnPlaylists() {
                if Task.isCancelled {
                    break
                }

                let orderedPlaylists = playlists.orderedForPresentation()
                let cards = orderedPlaylists.enumerated().map { index, playlist in
                    playlist.toLibraryCard(index: index)
                }

                state.content = MusicLibraryContent(cards: cards)
                state.isLoading = false
            }
        }

        refreshTask = Task { [weak self] in
            guard let self else { return }
            state.isLoading = true

            do {
                try await refreshLibrary()
            } catch {
                state.isLoading = false
            }
        }
    }

    public func send(_ action: MusicLibraryAction) {
        switch action {
        case let .playlistTapped(ref):
            guard
                let playlist = state.content?.cards.compactMap({ card -> PlaylistCard? in
                    if case let .playlist(value) = card {
                        return value
                    }
                    return nil
                }).first(where: { $0.ref == ref })
            else {
                return
            }

            state.selectedPlaylist = MusicLibraryDestination(
                ref: playlist.ref,
                title: playlist.title,
                role: playlist.role
            )

        case .dismissPlaylistDetail:
            state.selectedPlaylist = nil
        }
    }
}

private extension PlaylistSummary {
    func toLibraryCard(index: Int) -> MusicLibraryCard {
        let isFavourites = role == .favourites
        let presentationSeed = libraryPresentationSeed()

        return .playlist(
            PlaylistCard(
                ref: PlaylistRef(provider: provider, id: id),
                title: title,
                trackCount: trackCount,
                role: role,
                layout: isFeaturedCard(index: index) ? .featured : .compact,
                style: isFavourites
                    ? .surface4
                    : playlistCardStyles[
                        Int(abs(Int32(truncatingIfNeeded: presentationSeed))) % playlistCardStyles.count
                    ],
                artwork: isFavourites ? .favourites : .default,
                artworkSeed: presentationSeed
            )
        )
    }

    func libraryPresentationSeed() -> Int {
        playlistUuid?.rawValue.hashValue ?? id.hashValue
    }
}

private extension Array where Element == PlaylistSummary {
    func orderedForPresentation() -> [PlaylistSummary] {
        guard let favourites = first(where: { $0.role == .favourites }) else {
            return self
        }

        let regularPlaylists = filter { $0.role != .favourites }
        var result: [PlaylistSummary] = []

        if let firstRegular = regularPlaylists.first {
            result.append(firstRegular)
        }
        result.append(favourites)
        result.append(contentsOf: regularPlaylists.dropFirst())

        return result
    }
}

private func isFeaturedCard(index: Int) -> Bool {
    index >= firstFeaturedCardIndex && index % featuredCardPeriod == firstFeaturedCardIndex
}

private let firstFeaturedCardIndex = 2
private let featuredCardPeriod = 3
