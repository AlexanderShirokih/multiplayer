import Foundation

public protocol MusicLibraryRepository: Sendable {
    func observeAvailability() -> AsyncStream<MusicServiceAvailability>
    func observeOwnPlaylists() -> AsyncStream<[PlaylistSummary]>
    func observePlaylist(id: PlaylistId) -> AsyncStream<Playlist?>
    func observeSavedTracks() -> AsyncStream<SavedTracksResult>
    func observeTracks(refs: [TrackRef]) -> AsyncStream<[Track]>

    func refreshAvailability() async throws
    func refreshOwnPlaylists() async throws
    func refreshPlaylist(id: PlaylistId) async throws
    func refreshSavedTracks() async throws
    func refreshTracks(refs: [TrackRef]) async throws
}
