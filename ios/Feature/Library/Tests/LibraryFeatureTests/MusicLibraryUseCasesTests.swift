import CoreDomain
import LibraryFeature
import XCTest

/// Проверка, что use case делегируют вызовы в `MusicLibraryRepository` без дополнительной логики.
final class MusicLibraryUseCasesTests: XCTestCase {
    func testObserveOwnPlaylistsForwardsToRepository() {
        let spy = SpyMusicLibraryRepository()
        let useCase = ObserveOwnPlaylistsUseCase(repository: spy)
        _ = useCase()
        XCTAssertEqual(spy.observeOwnPlaylistsCallCount, 1)
    }

    func testRefreshLibraryForwardsToRepository() async throws {
        let spy = SpyMusicLibraryRepository()
        let useCase = RefreshLibraryUseCase(repository: spy)
        try await useCase()
        XCTAssertEqual(spy.refreshOwnPlaylistsCallCount, 1)
    }

    func testObservePlaylistForwardsIdToRepository() {
        let spy = SpyMusicLibraryRepository()
        let useCase = ObservePlaylistUseCase(repository: spy)
        let playlistId = PlaylistId(
            ownerId: ProviderUserId(rawValue: "user-1"),
            kind: PlaylistKind(rawValue: 1008)
        )
        _ = useCase(id: playlistId)
        XCTAssertEqual(spy.observePlaylistCallCount, 1)
        XCTAssertEqual(spy.lastObservePlaylistId, playlistId)
    }

    func testRefreshPlaylistForwardsIdToRepository() async throws {
        let spy = SpyMusicLibraryRepository()
        let useCase = RefreshPlaylistUseCase(repository: spy)
        let playlistId = PlaylistId(
            ownerId: ProviderUserId(rawValue: "user-2"),
            kind: PlaylistKind(rawValue: 3)
        )
        try await useCase(id: playlistId)
        XCTAssertEqual(spy.refreshPlaylistCallCount, 1)
        XCTAssertEqual(spy.lastRefreshPlaylistId, playlistId)
    }

    func testObserveSavedTracksForwardsToRepository() {
        let spy = SpyMusicLibraryRepository()
        let useCase = ObserveSavedTracksUseCase(repository: spy)
        _ = useCase()
        XCTAssertEqual(spy.observeSavedTracksCallCount, 1)
    }

    func testRefreshSavedTracksForwardsToRepository() async throws {
        let spy = SpyMusicLibraryRepository()
        let useCase = RefreshSavedTracksUseCase(repository: spy)
        try await useCase()
        XCTAssertEqual(spy.refreshSavedTracksCallCount, 1)
    }
}

// MARK: - Spy

private final class SpyMusicLibraryRepository: MusicLibraryRepository, @unchecked Sendable {
    var observeOwnPlaylistsCallCount = 0
    var refreshOwnPlaylistsCallCount = 0
    var observePlaylistCallCount = 0
    var lastObservePlaylistId: PlaylistId?
    var refreshPlaylistCallCount = 0
    var lastRefreshPlaylistId: PlaylistId?
    var observeSavedTracksCallCount = 0
    var refreshSavedTracksCallCount = 0

    func observeAvailability() -> AsyncStream<MusicServiceAvailability> {
        emptyFinishedStream()
    }

    func observeOwnPlaylists() -> AsyncStream<[PlaylistSummary]> {
        observeOwnPlaylistsCallCount += 1
        return emptyFinishedStream()
    }

    func observePlaylist(id: PlaylistId) -> AsyncStream<Playlist?> {
        observePlaylistCallCount += 1
        lastObservePlaylistId = id
        return emptyFinishedStream()
    }

    func observeSavedTracks() -> AsyncStream<SavedTracksResult> {
        observeSavedTracksCallCount += 1
        return emptyFinishedStream()
    }

    func observeTracks(refs: [TrackRef]) -> AsyncStream<[Track]> {
        emptyFinishedStream()
    }

    func refreshAvailability() async throws {}

    func refreshOwnPlaylists() async throws {
        refreshOwnPlaylistsCallCount += 1
    }

    func refreshPlaylist(id: PlaylistId) async throws {
        refreshPlaylistCallCount += 1
        lastRefreshPlaylistId = id
    }

    func refreshSavedTracks() async throws {
        refreshSavedTracksCallCount += 1
    }

    func refreshTracks(refs: [TrackRef]) async throws {}
}

private func emptyFinishedStream<T: Sendable>() -> AsyncStream<T> {
    AsyncStream { continuation in
        continuation.finish()
    }
}
