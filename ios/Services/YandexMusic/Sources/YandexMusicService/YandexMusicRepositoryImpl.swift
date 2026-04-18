import AuthFeature
import CoreDomain
import Foundation

// MARK: - Repository

public final class YandexMusicProvider: MusicProvider, @unchecked Sendable {
    public let id: MusicProviderId = .yandexMusic

    private let requestRunner: YandexMusicRequestRunner

    private let availabilityState = AsyncValueRelay<MusicServiceAvailability?>(nil)
    private let playlistsState = AsyncValueRelay<[PlaylistSummary]>([])
    private let playlistState = AsyncValueRelay<[PlaylistId: Playlist]>([:])
    private let savedTracksState = AsyncValueRelay<SavedTracksResult?>(nil)
    private let trackState = AsyncValueRelay<[TrackRef: Track]>([:])

    public init(
        accessTokenProvider: YandexAccessTokenProvider,
        api: YandexMusicAPI,
        urlSession: URLSession = .shared
    ) {
        requestRunner = YandexMusicRequestRunner(
            accessTokenProvider: accessTokenProvider,
            api: api
        )
    }

    public convenience init(
        accessTokenProvider: YandexAccessTokenProvider,
        urlSession: URLSession = .shared
    ) {
        self.init(
            accessTokenProvider: accessTokenProvider,
            api: URLSessionYandexMusicAPI(session: urlSession)
        )
    }

    // MARK: - Observe

    public func observeAvailability() -> AsyncStream<MusicServiceAvailability> {
        filterNonNil(availabilityState)
    }

    public func observePlaylists() -> AsyncStream<[PlaylistSummary]> {
        playlistsState.stream()
    }

    public func observePlaylist(id: PlaylistId) -> AsyncStream<Playlist?> {
        mapped(playlistState) { $0[id] }
    }

    public func observeSavedTracks() -> AsyncStream<SavedTracksResult> {
        filterNonNil(savedTracksState)
    }

    public func observeTracks(refs: [TrackRef]) -> AsyncStream<[Track]> {
        mapped(trackState) { tracks in refs.compactMap { tracks[$0] } }
    }

    // MARK: - Refresh

    public func refreshAvailability() async throws {
        let availability = try await requestRunner.withAuthorizedRequest { accessToken in
            let json = try await self.requestRunner.api.fetchAvailability(accessToken: accessToken)
            return try json.toAvailability()
        }
        availabilityState.yield(availability)
    }

    public func refreshPlaylists() async throws {
        let summaries = try await requestRunner.withCurrentUserId { accessToken, userId in
            let items = try await self.requestRunner.api.fetchOwnPlaylists(
                accessToken: accessToken,
                userId: userId.rawValue
            )
            return try items.map { try $0.toPlaylistSummary() }
        }
        playlistsState.yield(summaries)
    }

    public func refreshPlaylist(id: PlaylistId) async throws {
        let playlist = try await requestRunner.withAuthorizedRequest { accessToken in
            let json = try await self.requestRunner.api.fetchPlaylist(
                accessToken: accessToken,
                userId: id.ownerId.rawValue,
                kind: id.kind.rawValue
            )
            return try json.toPlaylist()
        }
        playlistState.yield(playlistState.currentValue.merging([id: playlist]) { $1 })
        cacheTracks(playlist.tracks.compactMap(\.track))
    }

    public func refreshSavedTracks() async throws {
        let result = try await requestRunner.withCurrentUserId { accessToken, userId in
            let raw = try await self.requestRunner.api.fetchSavedTracks(
                accessToken: accessToken,
                userId: userId.rawValue
            )
            return try parseSavedTracksResult(from: raw, userId: userId)
        }
        savedTracksState.yield(result)
    }

    public func refreshTracks(refs: [TrackRef]) async throws {
        guard !refs.isEmpty else { return }
        let loaded = try await requestRunner.withAuthorizedRequest { accessToken in
            let items = try await self.requestRunner.api.fetchTracks(
                accessToken: accessToken,
                trackIds: refs.map { $0.toApiTrackId() }
            )
            return try items.map { try $0.toTrack() }
        }
        cacheTracks(loaded)
    }

    // MARK: - Private helpers

    private func cacheTracks(_ tracks: [Track]) {
        guard !tracks.isEmpty else { return }
        let additions = Dictionary(
            tracks.map { ($0.preview.ref, $0) },
            uniquingKeysWith: { $1 }
        )
        trackState.yield(trackState.currentValue.merging(additions) { $1 })
    }

    private func filterNonNil<T: Sendable>(
        _ relay: AsyncValueRelay<T?>
    ) -> AsyncStream<T> {
        AsyncStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            let task = Task {
                for await value in relay.stream() {
                    guard let value else { continue }
                    continuation.yield(value)
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    private func mapped<Source: Sendable, Element: Sendable>(
        _ relay: AsyncValueRelay<Source>,
        transform: @escaping @Sendable (Source) -> Element
    ) -> AsyncStream<Element> {
        AsyncStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            let task = Task {
                for await value in relay.stream() {
                    continuation.yield(transform(value))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}

// MARK: - Request runner

final class YandexMusicRequestRunner: @unchecked Sendable {
    let api: YandexMusicAPI
    private let accessTokenProvider: YandexAccessTokenProvider
    private let lock = NSLock()
    private var _cachedUserId: ProviderUserId?

    init(accessTokenProvider: YandexAccessTokenProvider, api: YandexMusicAPI) {
        self.accessTokenProvider = accessTokenProvider
        self.api = api
    }

    func withCurrentUserId<T: Sendable>(
        _ block: @Sendable (String, ProviderUserId) async throws -> T
    ) async throws -> T {
        try await withAuthorizedRequest { accessToken in
            let userId = try await self.requireCurrentUserId(accessToken: accessToken)
            return try await block(accessToken, userId)
        }
    }

    func withAuthorizedRequest<T: Sendable>(
        _ block: @Sendable (String) async throws -> T
    ) async throws -> T {
        let token = try await accessTokenProvider.validAccessToken(forceRefresh: false)
        do {
            return try await block(token)
        } catch MusicLibraryError.unauthorized {
            let freshToken = try await accessTokenProvider.validAccessToken(forceRefresh: true)
            return try await block(freshToken)
        }
    }

    private func requireCurrentUserId(accessToken: String) async throws -> ProviderUserId {
        if let cached = lock.withLock({ _cachedUserId }) {
            return cached
        }
        let json = try await api.fetchCurrentUser(accessToken: accessToken)
        let userId = try json.toCurrentUserId()
        lock.withLock { _cachedUserId = userId }
        return userId
    }
}
