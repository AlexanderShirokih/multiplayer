import CoreDomain
import Foundation

public final class DefaultMusicLibrary: MusicLibrary, @unchecked Sendable {
    private let providersById: [MusicProviderId: MusicProvider]
    private let orderedProviders: [MusicProvider]

    public init(providers: Set<AnyMusicProvider>) {
        let baseProviders = providers.map(\.base)
        providersById = Dictionary(uniqueKeysWithValues: baseProviders.map { ($0.id, $0) })
        orderedProviders = baseProviders.sorted { lhs, rhs in
            providerRank(lhs.id) < providerRank(rhs.id)
        }
    }

    public convenience init(providers: [MusicProvider]) {
        self.init(providers: Set(providers.map(AnyMusicProvider.init)))
    }

    public func observeAvailability() -> AsyncStream<MusicServiceAvailability> {
        guard !orderedProviders.isEmpty else {
            return stream(
                once: MusicServiceAvailability(isAvailable: false, region: nil, permissions: [])
            )
        }

        return combineLatest(orderedProviders.map { provider in
            { provider.observeAvailability() }
        }) { values in
            values.reduce(
                into: MusicServiceAvailability(isAvailable: false, region: nil, permissions: [])
            ) { partial, next in
                partial = MusicServiceAvailability(
                    isAvailable: partial.isAvailable || next.isAvailable,
                    region: partial.region ?? next.region,
                    permissions: partial.permissions.union(next.permissions)
                )
            }
        }
    }

    public func observeAllPlaylists() -> AsyncStream<[PlaylistSummary]> {
        guard !orderedProviders.isEmpty else {
            return stream(once: [])
        }

        return combineLatest(orderedProviders.map { provider in
            { provider.observePlaylists() }
        }) { playlists in
            playlists
                .flatMap { $0 }
                .sorted {
                    let lhsRank = providerRank($0.provider)
                    let rhsRank = providerRank($1.provider)
                    if lhsRank != rhsRank {
                        return lhsRank < rhsRank
                    }
                    return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
                }
        }
    }

    public func observePlaylist(ref: PlaylistRef) -> AsyncStream<Playlist?> {
        provider(for: ref.provider).observePlaylist(id: ref.id)
    }

    public func observeSavedTracks() -> AsyncStream<SavedTracksResult> {
        let savedTracksProviders = orderedProviders.filter { $0.id == .yandexMusic }
        guard !savedTracksProviders.isEmpty else {
            return stream(once: .privateLibrary)
        }

        return combineLatest(savedTracksProviders.map { provider in
            { provider.observeSavedTracks() }
        }) { results in
            results.first(where: { result in
                if case .privateLibrary = result {
                    return false
                }
                return true
            }) ?? .privateLibrary
        }
    }

    public func refreshAll() async throws {
        try await withThrowingTaskGroup(of: Void.self) { group in
            for provider in orderedProviders {
                group.addTask {
                    try await provider.refreshAvailability()
                    try await provider.refreshPlaylists()
                }
            }
            try await group.waitForAll()
        }
    }

    public func refreshPlaylist(ref: PlaylistRef) async throws {
        try await provider(for: ref.provider).refreshPlaylist(id: ref.id)
    }

    public func refreshSavedTracks() async throws {
        try await withThrowingTaskGroup(of: Void.self) { group in
            for provider in orderedProviders {
                group.addTask {
                    try await provider.refreshSavedTracks()
                }
            }
            try await group.waitForAll()
        }
    }

    private func provider(for id: MusicProviderId) -> MusicProvider {
        guard let provider = providersById[id] else {
            fatalError("Music provider \(id) is not registered.")
        }
        return provider
    }
}

public struct AnyMusicProvider: Hashable, Sendable {
    public let base: MusicProvider
    private let id = UUID()

    public init(_ base: MusicProvider) {
        self.base = base
    }

    public static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.id == rhs.id
    }

    public func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

private func providerRank(_ providerId: MusicProviderId) -> Int {
    switch providerId {
    case .device:
        return 0

    case .yandexMusic:
        return 1
    }
}
