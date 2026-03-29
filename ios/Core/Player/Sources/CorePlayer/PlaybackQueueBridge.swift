import CoreDomain
import Foundation

public struct PlaybackQueueItem: Equatable, Sendable {
    public let id: String
    public let trackId: TrackId
    public let title: String
    public let subtitle: String
    /// Длительность трека в миллисекундах.
    public let durationMs: Int64

    public init(
        id: String,
        trackId: TrackId,
        title: String,
        subtitle: String,
        durationMs: Int64
    ) {
        self.id = id
        self.trackId = trackId
        self.title = title
        self.subtitle = subtitle
        self.durationMs = durationMs
    }
}

public struct PlaybackQueueState: Equatable, Sendable {
    public let queue: [PlaybackQueueItem]
    public let currentIndex: Int?
    public let isPlaying: Bool
    public let currentPositionMs: Int64
    public let controlsEnabled: Bool

    public init(
        queue: [PlaybackQueueItem] = [],
        currentIndex: Int? = nil,
        isPlaying: Bool = false,
        currentPositionMs: Int64 = 0,
        controlsEnabled: Bool? = nil
    ) {
        self.queue = queue
        self.currentIndex = currentIndex
        self.isPlaying = isPlaying
        self.currentPositionMs = currentPositionMs
        self.controlsEnabled = controlsEnabled ?? !queue.isEmpty
    }

    public var currentItem: PlaybackQueueItem? {
        guard let currentIndex else { return nil }
        return queue.indices.contains(currentIndex) ? queue[currentIndex] : nil
    }
}

public struct NowPlayingStripExternalState: Equatable, Sendable {
    public let title: String
    public let subtitle: String
    public let isPlaying: Bool
    public let currentPositionMs: Int64
    public let durationMs: Int64
    public let controlsEnabled: Bool

    public init(
        title: String = "",
        subtitle: String = "",
        isPlaying: Bool = false,
        currentPositionMs: Int64 = 0,
        durationMs: Int64 = 0,
        controlsEnabled: Bool = true
    ) {
        self.title = title
        self.subtitle = subtitle
        self.isPlaying = isPlaying
        self.currentPositionMs = currentPositionMs
        self.durationMs = durationMs
        self.controlsEnabled = controlsEnabled
    }
}

public protocol NowPlayingStripController: Sendable {
    func stripStateStream() -> AsyncStream<NowPlayingStripExternalState>
    func play() async
    func pause() async
    func skipNext() async
    func skipPrevious() async
    func seekTo(positionMs: Int64) async
}

public protocol PlaybackQueueBridge: NowPlayingStripController {
    func playbackStateStream() -> AsyncStream<PlaybackQueueState>
    func replaceQueue(queue: [PlaybackQueueItem], startIndex: Int?, autoPlay: Bool) async
    func playTrack(index: Int) async
}
