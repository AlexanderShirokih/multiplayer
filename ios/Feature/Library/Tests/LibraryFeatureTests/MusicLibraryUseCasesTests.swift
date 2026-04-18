import CoreDomain
import LibraryFeature
import XCTest

/// Проверка, что use case делегируют вызовы в `MusicLibrary` без дополнительной логики.
final class MusicLibraryUseCasesTests: XCTestCase {
    func testObserveOwnPlaylistsForwardsToRepository() {
        let spy = SpyMusicLibrary()
        let useCase = ObserveOwnPlaylistsUseCase(library: spy)
        _ = useCase()
        XCTAssertEqual(spy.observeAllPlaylistsCallCount, 1)
    }

    func testRefreshLibraryForwardsToRepository() async throws {
        let spy = SpyMusicLibrary()
        let useCase = RefreshLibraryUseCase(library: spy)
        try await useCase()
        XCTAssertEqual(spy.refreshAllCallCount, 1)
    }

    func testObservePlaylistForwardsIdToRepository() {
        let spy = SpyMusicLibrary()
        let useCase = ObservePlaylistUseCase(library: spy)
        let ref = PlaylistRef(
            provider: .device,
            id: PlaylistId(
            ownerId: ProviderUserId(rawValue: "user-1"),
            kind: PlaylistKind(rawValue: 1008)
            )
        )
        _ = useCase(ref: ref)
        XCTAssertEqual(spy.observePlaylistCallCount, 1)
        XCTAssertEqual(spy.lastObservePlaylistRef, ref)
    }

    func testRefreshPlaylistForwardsIdToRepository() async throws {
        let spy = SpyMusicLibrary()
        let useCase = RefreshPlaylistUseCase(library: spy)
        let ref = PlaylistRef(
            provider: .yandexMusic,
            id: PlaylistId(
            ownerId: ProviderUserId(rawValue: "user-2"),
            kind: PlaylistKind(rawValue: 3)
            )
        )
        try await useCase(ref: ref)
        XCTAssertEqual(spy.refreshPlaylistCallCount, 1)
        XCTAssertEqual(spy.lastRefreshPlaylistRef, ref)
    }

    func testObserveSavedTracksForwardsToRepository() {
        let spy = SpyMusicLibrary()
        let useCase = ObserveSavedTracksUseCase(library: spy)
        _ = useCase()
        XCTAssertEqual(spy.observeSavedTracksCallCount, 1)
    }

    func testRefreshSavedTracksForwardsToRepository() async throws {
        let spy = SpyMusicLibrary()
        let useCase = RefreshSavedTracksUseCase(library: spy)
        try await useCase()
        XCTAssertEqual(spy.refreshSavedTracksCallCount, 1)
    }
}

// MARK: - Spy

private final class SpyMusicLibrary: MusicLibrary, @unchecked Sendable {
    var observeAllPlaylistsCallCount = 0
    var refreshAllCallCount = 0
    var observePlaylistCallCount = 0
    var lastObservePlaylistRef: PlaylistRef?
    var refreshPlaylistCallCount = 0
    var lastRefreshPlaylistRef: PlaylistRef?
    var observeSavedTracksCallCount = 0
    var refreshSavedTracksCallCount = 0

    func observeAvailability() -> AsyncStream<MusicServiceAvailability> {
        emptyFinishedStream()
    }

    func observeAllPlaylists() -> AsyncStream<[PlaylistSummary]> {
        observeAllPlaylistsCallCount += 1
        return emptyFinishedStream()
    }

    func observePlaylist(ref: PlaylistRef) -> AsyncStream<Playlist?> {
        observePlaylistCallCount += 1
        lastObservePlaylistRef = ref
        return emptyFinishedStream()
    }

    func observeSavedTracks() -> AsyncStream<SavedTracksResult> {
        observeSavedTracksCallCount += 1
        return emptyFinishedStream()
    }

    func refreshAvailability() async throws {}

    func refreshAll() async throws {
        refreshAllCallCount += 1
    }

    func refreshPlaylist(ref: PlaylistRef) async throws {
        refreshPlaylistCallCount += 1
        lastRefreshPlaylistRef = ref
    }

    func refreshSavedTracks() async throws {
        refreshSavedTracksCallCount += 1
    }
}

private func emptyFinishedStream<T: Sendable>() -> AsyncStream<T> {
    AsyncStream { continuation in
        continuation.finish()
    }
}
