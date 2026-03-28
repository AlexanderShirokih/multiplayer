import CoreDomain
import Foundation

public struct ObserveOwnPlaylistsUseCase: Sendable {
    private let repository: MusicLibraryRepository

    public init(repository: MusicLibraryRepository) {
        self.repository = repository
    }

    public func callAsFunction() -> AsyncStream<[PlaylistSummary]> {
        repository.observeOwnPlaylists()
    }
}

public struct RefreshLibraryUseCase: Sendable {
    private let repository: MusicLibraryRepository

    public init(repository: MusicLibraryRepository) {
        self.repository = repository
    }

    public func callAsFunction() async throws {
        try await repository.refreshOwnPlaylists()
    }
}

public struct ObservePlaylistUseCase: Sendable {
    private let repository: MusicLibraryRepository

    public init(repository: MusicLibraryRepository) {
        self.repository = repository
    }

    public func callAsFunction(id: PlaylistId) -> AsyncStream<Playlist?> {
        repository.observePlaylist(id: id)
    }
}

public struct RefreshPlaylistUseCase: Sendable {
    private let repository: MusicLibraryRepository

    public init(repository: MusicLibraryRepository) {
        self.repository = repository
    }

    public func callAsFunction(id: PlaylistId) async throws {
        try await repository.refreshPlaylist(id: id)
    }
}

public struct ObserveSavedTracksUseCase: Sendable {
    private let repository: MusicLibraryRepository

    public init(repository: MusicLibraryRepository) {
        self.repository = repository
    }

    public func callAsFunction() -> AsyncStream<SavedTracksResult> {
        repository.observeSavedTracks()
    }
}

public struct RefreshSavedTracksUseCase: Sendable {
    private let repository: MusicLibraryRepository

    public init(repository: MusicLibraryRepository) {
        self.repository = repository
    }

    public func callAsFunction() async throws {
        try await repository.refreshSavedTracks()
    }
}
