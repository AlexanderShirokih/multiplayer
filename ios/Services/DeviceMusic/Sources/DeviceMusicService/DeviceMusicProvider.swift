import CoreDomain
import Foundation

private let devicePlaylistId = PlaylistId(
    ownerId: ProviderUserId(rawValue: "device"),
    kind: PlaylistKind(rawValue: 0)
)

public final class DeviceMusicProvider: MusicProvider, @unchecked Sendable {
    public let id: MusicProviderId = .device

    private let authorizationController: DeviceMediaAuthorizationController
    private let mediaLibrary: DeviceMediaLibrary
    private let documentsLibrary: DocumentsAudioLibrary
    private let availabilityRelay = AsyncValueRelay(
        MusicServiceAvailability(isAvailable: true, region: nil, permissions: [])
    )
    private let playlistRelay = AsyncValueRelay<Playlist>(devicePlaylist(tracks: []))
    private let urlStore = DeviceTrackURLStore()
    private var sourceObservers: [Task<Void, Never>] = []

    init(
        authorizationController: DeviceMediaAuthorizationController,
        mediaLibrary: DeviceMediaLibrary,
        documentsLibrary: DocumentsAudioLibrary
    ) {
        self.authorizationController = authorizationController
        self.mediaLibrary = mediaLibrary
        self.documentsLibrary = documentsLibrary
        sourceObservers = [
            observe(stream: mediaLibrary.changeStream()),
            observe(stream: documentsLibrary.changeStream())
        ]
    }

    deinit {
        sourceObservers.forEach { $0.cancel() }
    }

    public func observeAvailability() -> AsyncStream<MusicServiceAvailability> {
        availabilityRelay.stream()
    }

    public func observePlaylists() -> AsyncStream<[PlaylistSummary]> {
        AsyncStream { continuation in
            let task = Task {
                for await playlist in playlistRelay.stream() {
                    continuation.yield([playlist.summary])
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    public func observePlaylist(id: PlaylistId) -> AsyncStream<Playlist?> {
        AsyncStream { continuation in
            let task = Task {
                for await playlist in playlistRelay.stream() {
                    continuation.yield(id == devicePlaylistId ? playlist : nil)
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    public func observeSavedTracks() -> AsyncStream<SavedTracksResult> {
        AsyncStream { continuation in
            continuation.yield(.privateLibrary)
            continuation.finish()
        }
    }

    public func refreshAvailability() async throws {}

    public func refreshPlaylists() async throws {
        await refresh()
    }

    public func refreshPlaylist(id: PlaylistId) async throws {
        guard id == devicePlaylistId else { return }
        await refresh()
    }

    public func refreshSavedTracks() async throws {}

    func streamURL(for trackId: TrackId) async throws -> URL {
        guard let url = await urlStore.url(for: trackId) else {
            throw MusicLibraryError.invalidResponse(
                description: "Missing local device URL for track \(trackId.rawValue)."
            )
        }
        return url
    }

    private func refresh() async {
        let mediaTracks: [DeviceMappedTrack]
        if authorizationController.authorizationStatus() == .authorized {
            mediaTracks = DeviceMediaItemMapper.map(mediaLibrary.fetchAudioTracks())
        } else {
            mediaTracks = []
        }

        let documentFiles = await documentsLibrary.fetchAudioFiles()
        let documentTracks = DocumentsAudioMapper.map(
            documentFiles,
            startingAt: mediaTracks.count
        )

        let allTracks = mediaTracks + documentTracks
        let urlMap = Dictionary(
            uniqueKeysWithValues: allTracks.map { ($0.entry.trackRef.trackId, $0.assetURL) }
        )
        await urlStore.replace(with: urlMap)
        playlistRelay.yield(devicePlaylist(tracks: allTracks.map(\.entry)))
    }

    private func observe(stream: AsyncStream<Void>) -> Task<Void, Never> {
        Task { [weak self] in
            for await _ in stream {
                if Task.isCancelled { break }
                await self?.refresh()
            }
        }
    }
}

public final class DeviceTrackStreamUrlProvider: TrackStreamUrlProvider, @unchecked Sendable {
    private let provider: DeviceMusicProvider

    init(provider: DeviceMusicProvider) {
        self.provider = provider
    }

    public func streamURL(for trackId: TrackId) async throws -> URL {
        try await provider.streamURL(for: trackId)
    }
}

private func devicePlaylist(tracks: [PlaylistTrackEntry]) -> Playlist {
    Playlist(
        summary: PlaylistSummary(
            id: devicePlaylistId,
            provider: .device,
            playlistUuid: nil,
            title: "Треки с устройства",
            ownerName: nil,
            coverUriTemplate: nil,
            trackCount: tracks.count,
            durationMs: tracks.compactMap { $0.track?.preview.durationMs }.reduce(0, +).nonZero,
            isAvailable: true,
            isCollective: false,
            visibility: .private,
            role: .regular
        ),
        revision: nil,
        snapshot: nil,
        likesCount: nil,
        tracks: tracks
    )
}

private actor DeviceTrackURLStore {
    private var values: [TrackId: URL] = [:]

    func replace(with values: [TrackId: URL]) {
        self.values = values
    }

    func url(for trackId: TrackId) -> URL? {
        values[trackId]
    }
}

private extension Int64 {
    var nonZero: Int64? {
        self > 0 ? self : nil
    }
}
