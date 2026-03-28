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
