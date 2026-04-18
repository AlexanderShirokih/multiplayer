import Foundation

public protocol MusicProvider: Sendable {
    var id: MusicProviderId { get }

    func observeAvailability() -> AsyncStream<MusicServiceAvailability>
    func observePlaylists() -> AsyncStream<[PlaylistSummary]>
    func observePlaylist(id: PlaylistId) -> AsyncStream<Playlist?>
    func observeSavedTracks() -> AsyncStream<SavedTracksResult>

    func refreshAvailability() async throws
    func refreshPlaylists() async throws
    func refreshPlaylist(id: PlaylistId) async throws
    func refreshSavedTracks() async throws
}

public protocol MusicLibrary: Sendable {
    func observeAvailability() -> AsyncStream<MusicServiceAvailability>
    func observeAllPlaylists() -> AsyncStream<[PlaylistSummary]>
    func observePlaylist(ref: PlaylistRef) -> AsyncStream<Playlist?>
    func observeSavedTracks() -> AsyncStream<SavedTracksResult>

    func refreshAll() async throws
    func refreshPlaylist(ref: PlaylistRef) async throws
    func refreshSavedTracks() async throws
}
