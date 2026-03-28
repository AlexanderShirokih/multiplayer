import CoreDomain
import Foundation

enum MusicLibraryPreviewFactory {
    @MainActor
    static func makeViewModel() -> MusicLibraryViewModel {
        let repository = PreviewMusicLibraryRepository()
        return MusicLibraryViewModel(
            observeOwnPlaylists: ObserveOwnPlaylistsUseCase(repository: repository),
            refreshLibrary: RefreshLibraryUseCase(repository: repository)
        )
    }
}

private final class PreviewMusicLibraryRepository: MusicLibraryRepository, @unchecked Sendable {
    private let playlists: [PlaylistSummary] = [
        PlaylistSummary(
            id: PlaylistId(
                ownerId: ProviderUserId(rawValue: "preview"),
                kind: PlaylistKind(rawValue: 7)
            ),
            provider: .yandexMusic,
            playlistUuid: PlaylistUuid(rawValue: "evening-drive"),
            title: "Вечерний маршрут",
            ownerName: "preview",
            coverUriTemplate: nil,
            trackCount: 28,
            durationMs: nil,
            isAvailable: true,
            isCollective: false,
            visibility: .public
        ),
        PlaylistSummary(
            id: PlaylistId(
                ownerId: ProviderUserId(rawValue: "preview"),
                kind: PlaylistKind(rawValue: 3)
            ),
            provider: .yandexMusic,
            playlistUuid: PlaylistUuid(rawValue: "liked"),
            title: "Любимые",
            ownerName: "preview",
            coverUriTemplate: nil,
            trackCount: 324,
            durationMs: nil,
            isAvailable: true,
            isCollective: false,
            visibility: .private,
            role: .favourites
        ),
        PlaylistSummary(
            id: PlaylistId(
                ownerId: ProviderUserId(rawValue: "preview"),
                kind: PlaylistKind(rawValue: 14)
            ),
            provider: .yandexMusic,
            playlistUuid: PlaylistUuid(rawValue: "sunrise-set"),
            title: "Sunrise Set",
            ownerName: "preview",
            coverUriTemplate: nil,
            trackCount: 41,
            durationMs: nil,
            isAvailable: true,
            isCollective: false,
            visibility: .public
        ),
        PlaylistSummary(
            id: PlaylistId(
                ownerId: ProviderUserId(rawValue: "preview"),
                kind: PlaylistKind(rawValue: 19)
            ),
            provider: .yandexMusic,
            playlistUuid: PlaylistUuid(rawValue: "focus"),
            title: "Фокус без шума",
            ownerName: "preview",
            coverUriTemplate: nil,
            trackCount: 17,
            durationMs: nil,
            isAvailable: true,
            isCollective: false,
            visibility: .public
        ),
        PlaylistSummary(
            id: PlaylistId(
                ownerId: ProviderUserId(rawValue: "preview"),
                kind: PlaylistKind(rawValue: 22)
            ),
            provider: .yandexMusic,
            playlistUuid: PlaylistUuid(rawValue: "late-check-in"),
            title: "Late Check-In",
            ownerName: "preview",
            coverUriTemplate: nil,
            trackCount: 12,
            durationMs: nil,
            isAvailable: true,
            isCollective: false,
            visibility: .public
        )
    ]

    func observeAvailability() -> AsyncStream<MusicServiceAvailability> {
        AsyncStream { continuation in
            continuation.yield(
                MusicServiceAvailability(isAvailable: true, region: nil, permissions: [])
            )
            continuation.finish()
        }
    }

    func observeOwnPlaylists() -> AsyncStream<[PlaylistSummary]> {
        AsyncStream { continuation in
            continuation.yield(playlists)
            continuation.finish()
        }
    }

    func observePlaylist(id: PlaylistId) -> AsyncStream<Playlist?> {
        AsyncStream { continuation in
            continuation.yield(nil)
            continuation.finish()
        }
    }

    func observeSavedTracks() -> AsyncStream<SavedTracksResult> {
        AsyncStream { continuation in
            continuation.yield(.privateLibrary)
            continuation.finish()
        }
    }

    func observeTracks(refs: [TrackRef]) -> AsyncStream<[Track]> {
        AsyncStream { continuation in
            continuation.yield([])
            continuation.finish()
        }
    }

    func refreshAvailability() async throws {}
    func refreshOwnPlaylists() async throws {}
    func refreshPlaylist(id: PlaylistId) async throws {}
    func refreshSavedTracks() async throws {}
    func refreshTracks(refs: [TrackRef]) async throws {}
}
