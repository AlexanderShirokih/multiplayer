import CoreDomain
import LibraryFeature
import XCTest

@MainActor
final class TrackListViewModelTests: XCTestCase {
    func testRegularPlaylistRefreshPopulatesTrackListAndActivatesClickedTrack() async {
        let library = FakeMusicLibrary(playlist: playlistFixture())
        let playbackBridge = InMemoryPlaybackQueueBridge()
        let viewModel = TrackListViewModel(
            destination: MusicLibraryDestination(
                ref: PlaylistRef(provider: .yandexMusic, id: playlistFixtureId),
                title: "Road Trip",
                role: .regular
            ),
            observePlaylist: ObservePlaylistUseCase(library: library),
            refreshPlaylist: RefreshPlaylistUseCase(library: library),
            observeSavedTracks: ObserveSavedTracksUseCase(library: library),
            refreshSavedTracks: RefreshSavedTracksUseCase(library: library),
            playbackBridge: playbackBridge
        )

        viewModel.start()
        await waitUntil { viewModel.trackRows.count == 2 }

        viewModel.onTrackTap(index: 1)
        await waitUntil { viewModel.activeTrackIndex == 1 }

        XCTAssertEqual(viewModel.title, "Road Trip")
        XCTAssertEqual(viewModel.trackRows.count, 2)
        XCTAssertEqual(viewModel.trackRows[1].title, "Second Track")
        XCTAssertEqual(viewModel.activeTrackIndex, 1)
        XCTAssertNil(viewModel.status)
        XCTAssertEqual(library.refreshPlaylistCallCount, 1)
    }

    func testFavouritesRefreshPopulatesSavedTracksList() async {
        let library = FakeMusicLibrary(
            savedTracksResult: .available(
                SavedTracks(
                    ownerId: ProviderUserId(rawValue: "owner"),
                    revision: 5,
                    tracks: [
                        SavedTrackEntry(
                            position: 0,
                            addedAt: nil,
                            trackRef: trackRef("saved-track"),
                            track: trackPreview(title: "Saved Track", artist: "Saved Artist")
                        )
                    ]
                )
            )
        )
        let viewModel = TrackListViewModel(
            destination: MusicLibraryDestination(
                ref: PlaylistRef(provider: .yandexMusic, id: playlistFixtureId),
                title: "Любимые",
                role: .favourites
            ),
            observePlaylist: ObservePlaylistUseCase(library: library),
            refreshPlaylist: RefreshPlaylistUseCase(library: library),
            observeSavedTracks: ObserveSavedTracksUseCase(library: library),
            refreshSavedTracks: RefreshSavedTracksUseCase(library: library),
            playbackBridge: InMemoryPlaybackQueueBridge()
        )

        viewModel.start()
        await waitUntil { viewModel.trackRows.count == 1 }

        XCTAssertEqual(viewModel.title, "Любимые")
        XCTAssertEqual(viewModel.trackRows.count, 1)
        XCTAssertEqual(viewModel.trackRows.first?.title, "Saved Track")
        XCTAssertNil(viewModel.status)
        XCTAssertEqual(library.refreshSavedTracksCallCount, 1)
    }

    func testDeviceEmptyPlaylistWithDeniedPermissionShowsSettingsAction() async {
        let library = FakeMusicLibrary(playlist: emptyDevicePlaylist())
        let viewModel = TrackListViewModel(
            destination: MusicLibraryDestination(
                ref: PlaylistRef(provider: .device, id: playlistFixtureId),
                title: "Треки с устройства",
                role: .regular
            ),
            observePlaylist: ObservePlaylistUseCase(library: library),
            refreshPlaylist: RefreshPlaylistUseCase(library: library),
            observeSavedTracks: ObserveSavedTracksUseCase(library: library),
            refreshSavedTracks: RefreshSavedTracksUseCase(library: library),
            playbackBridge: InMemoryPlaybackQueueBridge(),
            requestDeviceMediaAccess: RequestDeviceMediaAccessUseCase(
                controller: FakeDeviceMediaAuthorizationController(status: .denied)
            )
        )

        viewModel.start()
        await waitUntil { viewModel.status == .permissionDenied }

        XCTAssertEqual(viewModel.feedbackActionTitle, "Открыть настройки")
        XCTAssertEqual(library.refreshPlaylistCallCount, 1)
    }

    func testDeviceNonEmptyPlaylistWithDeniedPermissionShowsTracks() async {
        let library = FakeMusicLibrary(playlist: playlistFixture())
        let viewModel = TrackListViewModel(
            destination: MusicLibraryDestination(
                ref: PlaylistRef(provider: .device, id: playlistFixtureId),
                title: "Треки с устройства",
                role: .regular
            ),
            observePlaylist: ObservePlaylistUseCase(library: library),
            refreshPlaylist: RefreshPlaylistUseCase(library: library),
            observeSavedTracks: ObserveSavedTracksUseCase(library: library),
            refreshSavedTracks: RefreshSavedTracksUseCase(library: library),
            playbackBridge: InMemoryPlaybackQueueBridge(),
            requestDeviceMediaAccess: RequestDeviceMediaAccessUseCase(
                controller: FakeDeviceMediaAuthorizationController(status: .denied)
            )
        )

        viewModel.start()
        await waitUntil { viewModel.trackRows.count == 2 }

        XCTAssertNil(viewModel.status)
        XCTAssertEqual(library.refreshPlaylistCallCount, 1)
    }

    private func waitUntil(
        _ condition: @escaping () -> Bool,
        timeoutNanoseconds: UInt64 = 1_000_000_000
    ) async {
        let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
        while !condition() {
            if DispatchTime.now().uptimeNanoseconds >= deadline {
                XCTFail("Condition not met before timeout")
                return
            }
            await Task.yield()
        }
    }
}

// MARK: - Test doubles

private final class FakeMusicLibrary: MusicLibrary, @unchecked Sendable {
    private let playlistRelay = AsyncValueRelay<Playlist?>(nil)
    private let savedTracksRelay = AsyncValueRelay<SavedTracksResult?>(nil)

    private let playlistFixture: Playlist?
    private let savedTracksFixture: SavedTracksResult?

    var refreshPlaylistCallCount = 0
    var refreshSavedTracksCallCount = 0

    init(playlist: Playlist? = nil, savedTracksResult: SavedTracksResult? = nil) {
        playlistFixture = playlist
        savedTracksFixture = savedTracksResult
    }

    func observeAvailability() -> AsyncStream<MusicServiceAvailability> {
        AsyncStream { continuation in
            continuation.finish()
        }
    }

    func observeAllPlaylists() -> AsyncStream<[PlaylistSummary]> {
        AsyncStream { continuation in
            continuation.finish()
        }
    }

    func observePlaylist(ref: PlaylistRef) -> AsyncStream<Playlist?> {
        playlistRelay.stream()
    }

    func observeSavedTracks() -> AsyncStream<SavedTracksResult> {
        AsyncStream { continuation in
            Task {
                var iterator = savedTracksRelay.stream().makeAsyncIterator()
                while let optional = await iterator.next() {
                    if let value = optional {
                        continuation.yield(value)
                    }
                }
            }
        }
    }

    func refreshAvailability() async throws {}

    func refreshAll() async throws {}

    func refreshPlaylist(ref: PlaylistRef) async throws {
        refreshPlaylistCallCount += 1
        playlistRelay.yield(playlistFixture)
    }

    func refreshSavedTracks() async throws {
        refreshSavedTracksCallCount += 1
        savedTracksRelay.yield(savedTracksFixture)
    }
}

private struct FakeDeviceMediaAuthorizationController: DeviceMediaAuthorizationController {
    let status: DeviceMediaAuthorizationStatus

    func authorizationStatus() -> DeviceMediaAuthorizationStatus {
        status
    }

    func requestAuthorization() async -> DeviceMediaAuthorizationStatus {
        status
    }
}

/// Ретранслятор значения в `AsyncStream` (как в `AuthFeature` / `YandexMusicService`).
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
        let activeContinuations = lock.withLock {
            value = newValue
            return Array(continuations.values)
        }
        activeContinuations.forEach { $0.yield(newValue) }
    }

    private func removeContinuation(id: UUID) {
        _ = lock.withLock {
            continuations.removeValue(forKey: id)
        }
    }
}

private extension NSLock {
    func withLock<T>(_ operation: () -> T) -> T {
        lock()
        defer { unlock() }
        return operation()
    }
}

// MARK: - Fixtures

private let playlistFixtureId = PlaylistId(
    ownerId: ProviderUserId(rawValue: "owner"),
    kind: PlaylistKind(rawValue: 42)
)

private func emptyDevicePlaylist() -> Playlist {
    Playlist(
        summary: PlaylistSummary(
            id: playlistFixtureId,
            provider: .device,
            playlistUuid: nil,
            title: "Треки с устройства",
            ownerName: nil,
            coverUriTemplate: nil,
            trackCount: 0,
            durationMs: nil,
            isAvailable: true,
            isCollective: false,
            visibility: .private,
            role: .regular
        ),
        revision: nil,
        snapshot: nil,
        likesCount: nil,
        tracks: []
    )
}

private func playlistFixture() -> Playlist {
    Playlist(
        summary: PlaylistSummary(
            id: playlistFixtureId,
            provider: .yandexMusic,
            playlistUuid: nil,
            title: "Road Trip",
            ownerName: nil,
            coverUriTemplate: nil,
            trackCount: 2,
            durationMs: 420_000,
            isAvailable: true,
            isCollective: false,
            visibility: nil,
            role: .regular
        ),
        revision: 1,
        snapshot: 1,
        likesCount: nil,
        tracks: [playlistFixtureTrackFirst(), playlistFixtureTrackSecond()]
    )
}

private func playlistFixtureTrackFirst() -> PlaylistTrackEntry {
    PlaylistTrackEntry(
        position: 0,
        addedAt: nil,
        originalIndex: nil,
        originalShuffleIndex: nil,
        isRecent: nil,
        trackRef: trackRef("first-track"),
        track: Track(
            preview: trackPreview(title: "First Track", artist: "Artist One"),
            lyricsAvailable: false,
            isAvailableForPremium: true,
            isAvailableWithoutPermission: false
        )
    )
}

private func playlistFixtureTrackSecond() -> PlaylistTrackEntry {
    PlaylistTrackEntry(
        position: 1,
        addedAt: nil,
        originalIndex: nil,
        originalShuffleIndex: nil,
        isRecent: nil,
        trackRef: trackRef("second-track"),
        track: Track(
            preview: trackPreview(title: "Second Track", artist: "Artist Two"),
            lyricsAvailable: false,
            isAvailableForPremium: true,
            isAvailableWithoutPermission: false
        )
    )
}

private func trackPreview(title: String, artist: String) -> TrackPreview {
    let ref = trackRef(title.lowercased().replacingOccurrences(of: " ", with: "-"))
    return TrackPreview(
        ref: ref,
        title: title,
        artists: [ArtistPreview(id: artist, name: artist)],
        durationMs: 180_000,
        coverUriTemplate: nil,
        isAvailable: true
    )
}

private func trackRef(_ trackIdValue: String) -> TrackRef {
    TrackRef(
        trackId: TrackId(rawValue: trackIdValue),
        albumId: AlbumId(rawValue: "album-\(trackIdValue)")
    )
}
