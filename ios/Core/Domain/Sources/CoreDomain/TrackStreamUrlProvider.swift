import Foundation

public protocol TrackStreamUrlProvider: Sendable {
    func streamURL(for trackId: TrackId) async throws -> URL
}

public protocol PlayableUrlResolver: Sendable {
    func playableURL(for item: PlaybackQueueItemDescriptor) async throws -> URL
}

public struct PlaybackQueueItemDescriptor: Sendable, Equatable {
    public let id: String
    public let trackId: TrackId
    public let source: PlayableSourceDescriptor

    public init(id: String, trackId: TrackId, source: PlayableSourceDescriptor) {
        self.id = id
        self.trackId = trackId
        self.source = source
    }
}

public enum PlayableSourceDescriptor: Sendable, Equatable {
    case remote(provider: MusicProviderId)
    case local(url: URL)
}
