import CoreDomain
import Foundation

enum MusicLibraryPreviewFactory {
    @MainActor
    static func makeViewModel() -> MusicLibraryViewModel {
        let library = PreviewMusicLibrary()
        return MusicLibraryViewModel(
            observeOwnPlaylists: ObserveOwnPlaylistsUseCase(library: library),
            refreshLibrary: RefreshLibraryUseCase(library: library)
        )
    }

    @MainActor
    static func makeTrackListViewModel(destination: MusicLibraryDestination) -> TrackListViewModel {
        let library = PreviewMusicLibrary()
        let bridge = InMemoryPlaybackQueueBridge()
        return TrackListViewModel(
            destination: destination,
            observePlaylist: ObservePlaylistUseCase(library: library),
            refreshPlaylist: RefreshPlaylistUseCase(library: library),
            observeSavedTracks: ObserveSavedTracksUseCase(library: library),
            refreshSavedTracks: RefreshSavedTracksUseCase(library: library),
            playbackBridge: bridge
        )
    }
}

private final class PreviewMusicLibrary: MusicLibrary, @unchecked Sendable {
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

    func observeAllPlaylists() -> AsyncStream<[PlaylistSummary]> {
        AsyncStream { continuation in
            continuation.yield(playlists)
            continuation.finish()
        }
    }

    func observePlaylist(ref: PlaylistRef) -> AsyncStream<Playlist?> {
        AsyncStream { continuation in
            continuation.yield(playlists.first(where: { $0.id == ref.id }).map { summary in
                Playlist(
                    summary: summary,
                    revision: nil,
                    snapshot: nil,
                    likesCount: nil,
                    tracks: []
                )
            })
            continuation.finish()
        }
    }

    func observeSavedTracks() -> AsyncStream<SavedTracksResult> {
        AsyncStream { continuation in
            continuation.yield(.privateLibrary)
            continuation.finish()
        }
    }

    func refreshAvailability() async throws {}
    func refreshAll() async throws {}
    func refreshPlaylist(ref: PlaylistRef) async throws {}
    func refreshSavedTracks() async throws {}
}
