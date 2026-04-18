import CoreDataLayer
import CoreDomain
import Foundation
import XCTest

final class DefaultMusicLibraryTests: XCTestCase {
    func testObserveAllPlaylistsMergesProvidersInStableOrder() async {
        let device = FakeMusicProvider(id: .device)
        let yandex = FakeMusicProvider(id: .yandexMusic)
        let library = DefaultMusicLibrary(providers: [yandex, device])

        let stream = library.observeAllPlaylists()
        device.playlistsRelay.yield([
            playlistSummary(id: 1, provider: .device, title: "Device")
        ])
        yandex.playlistsRelay.yield([
            playlistSummary(id: 2, provider: .yandexMusic, title: "Yandex")
        ])

        let playlists = await firstValue(from: stream)
        XCTAssertEqual(playlists.map(\.provider), [.device, .yandexMusic])
    }

    func testObservePlaylistRoutesByProvider() async {
        let device = FakeMusicProvider(id: .device)
        let yandex = FakeMusicProvider(id: .yandexMusic)
        let library = DefaultMusicLibrary(providers: [device, yandex])
        let ref = PlaylistRef(provider: .device, id: playlistId(owner: "device", kind: 10))

        let stream = library.observePlaylist(ref: ref)
        let playlist = Playlist(
            summary: playlistSummary(id: 10, provider: .device, title: "Device"),
            revision: nil,
            snapshot: nil,
            likesCount: nil,
            tracks: []
        )
        device.playlistRelay.yield([ref.id: playlist])

        let value = await firstValue(from: stream)
        XCTAssertEqual(value?.summary.provider, .device)
        XCTAssertEqual(device.observedPlaylistIDs, [ref.id])
    }

    func testRefreshAllRefreshesEveryProvider() async throws {
        let device = FakeMusicProvider(id: .device)
        let yandex = FakeMusicProvider(id: .yandexMusic)
        let library = DefaultMusicLibrary(providers: [device, yandex])

        try await library.refreshAll()

        XCTAssertEqual(device.refreshAvailabilityCallCount, 1)
        XCTAssertEqual(device.refreshPlaylistsCallCount, 1)
        XCTAssertEqual(yandex.refreshAvailabilityCallCount, 1)
        XCTAssertEqual(yandex.refreshPlaylistsCallCount, 1)
    }

    func testObserveAvailabilityReturnsAvailableWhenAnyProviderAvailable() async {
        let device = FakeMusicProvider(id: .device)
        let yandex = FakeMusicProvider(id: .yandexMusic)
        let library = DefaultMusicLibrary(providers: [device, yandex])

        let stream = library.observeAvailability()
        device.availabilityRelay.yield(
            MusicServiceAvailability(isAvailable: false, region: nil, permissions: [])
        )
        yandex.availabilityRelay.yield(
            MusicServiceAvailability(isAvailable: true, region: 225, permissions: ["streaming"])
        )

        let availability = await firstValue(from: stream)
        XCTAssertTrue(availability.isAvailable)
        XCTAssertEqual(availability.region, 225)
        XCTAssertEqual(availability.permissions, ["streaming"])
    }
}

private final class FakeMusicProvider: MusicProvider, @unchecked Sendable {
    let id: MusicProviderId
    let availabilityRelay = AsyncValueRelay(MusicServiceAvailability(isAvailable: false, region: nil, permissions: []))
    let playlistsRelay = AsyncValueRelay<[PlaylistSummary]>([])
    let playlistRelay = AsyncValueRelay<[PlaylistId: Playlist]>([:])
    let savedTracksRelay = AsyncValueRelay<SavedTracksResult>(.privateLibrary)

    private(set) var observedPlaylistIDs: [PlaylistId] = []
    private(set) var refreshAvailabilityCallCount = 0
    private(set) var refreshPlaylistsCallCount = 0

    init(id: MusicProviderId) {
        self.id = id
    }

    func observeAvailability() -> AsyncStream<MusicServiceAvailability> { availabilityRelay.stream() }
    func observePlaylists() -> AsyncStream<[PlaylistSummary]> { playlistsRelay.stream() }

    func observePlaylist(id: PlaylistId) -> AsyncStream<Playlist?> {
        observedPlaylistIDs.append(id)
        return AsyncStream { continuation in
            let task = Task {
                for await playlists in playlistRelay.stream() {
                    continuation.yield(playlists[id])
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func observeSavedTracks() -> AsyncStream<SavedTracksResult> { savedTracksRelay.stream() }

    func refreshAvailability() async throws { refreshAvailabilityCallCount += 1 }
    func refreshPlaylists() async throws { refreshPlaylistsCallCount += 1 }
    func refreshPlaylist(id: PlaylistId) async throws {}
    func refreshSavedTracks() async throws {}
}

private final class AsyncValueRelay<Value: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value
    private var continuations: [UUID: AsyncStream<Value>.Continuation] = [:]

    init(_ value: Value) {
        self.value = value
    }

    func stream() -> AsyncStream<Value> {
        AsyncStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            let id = UUID()
            let currentValue = lock.withLock {
                continuations[id] = continuation
                return value
            }
            continuation.yield(currentValue)
            continuation.onTermination = { [weak self] _ in
                self?.removeContinuation(id: id)
            }
        }
    }

    func yield(_ newValue: Value) {
        let continuations = lock.withLock {
            value = newValue
            return Array(self.continuations.values)
        }
        continuations.forEach { $0.yield(newValue) }
    }

    private func removeContinuation(id: UUID) {
        _ = lock.withLock {
            continuations.removeValue(forKey: id)
        }
    }
}

private func playlistId(owner: String, kind: Int64) -> PlaylistId {
    PlaylistId(ownerId: ProviderUserId(rawValue: owner), kind: PlaylistKind(rawValue: kind))
}

private func playlistSummary(id: Int64, provider: MusicProviderId, title: String) -> PlaylistSummary {
    PlaylistSummary(
        id: playlistId(owner: provider == .device ? "device" : "yandex", kind: id),
        provider: provider,
        playlistUuid: nil,
        title: title,
        ownerName: nil,
        coverUriTemplate: nil,
        trackCount: 0,
        durationMs: nil,
        isAvailable: true,
        isCollective: false,
        visibility: nil,
        role: .regular
    )
}

private func firstValue<T>(from stream: AsyncStream<T>) async -> T {
    var iterator = stream.makeAsyncIterator()
    guard let value = await iterator.next() else {
        fatalError("Stream completed without emitting a value")
    }
    return value
}

private extension NSLock {
    func withLock<T>(_ operation: () -> T) -> T {
        lock()
        defer { unlock() }
        return operation()
    }
}
