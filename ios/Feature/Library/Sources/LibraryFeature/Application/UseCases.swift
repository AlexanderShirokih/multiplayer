import CoreDomain
import Foundation

public struct ObserveOwnPlaylistsUseCase: Sendable {
    private let library: MusicLibrary

    public init(library: MusicLibrary) {
        self.library = library
    }

    public func callAsFunction() -> AsyncStream<[PlaylistSummary]> {
        library.observeAllPlaylists()
    }
}

public struct RefreshLibraryUseCase: Sendable {
    private let library: MusicLibrary

    public init(library: MusicLibrary) {
        self.library = library
    }

    public func callAsFunction() async throws {
        try await library.refreshAll()
    }
}

public struct ObservePlaylistUseCase: Sendable {
    private let library: MusicLibrary

    public init(library: MusicLibrary) {
        self.library = library
    }

    public func callAsFunction(ref: PlaylistRef) -> AsyncStream<Playlist?> {
        library.observePlaylist(ref: ref)
    }
}

public struct RefreshPlaylistUseCase: Sendable {
    private let library: MusicLibrary

    public init(library: MusicLibrary) {
        self.library = library
    }

    public func callAsFunction(ref: PlaylistRef) async throws {
        try await library.refreshPlaylist(ref: ref)
    }
}

public struct ObserveSavedTracksUseCase: Sendable {
    private let library: MusicLibrary

    public init(library: MusicLibrary) {
        self.library = library
    }

    public func callAsFunction() -> AsyncStream<SavedTracksResult> {
        library.observeSavedTracks()
    }
}

public struct RefreshSavedTracksUseCase: Sendable {
    private let library: MusicLibrary

    public init(library: MusicLibrary) {
        self.library = library
    }

    public func callAsFunction() async throws {
        try await library.refreshSavedTracks()
    }
}

public struct RequestDeviceMediaAccessUseCase: Sendable {
    private let controller: DeviceMediaAuthorizationController?

    public init(controller: DeviceMediaAuthorizationController?) {
        self.controller = controller
    }

    public func callAsFunction() async -> DeviceMediaAuthorizationStatus {
        guard let controller else {
            return .authorized
        }
        return await controller.requestAuthorization()
    }
}
