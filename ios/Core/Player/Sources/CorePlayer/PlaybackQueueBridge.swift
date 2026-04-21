import CoreDomain
import Foundation

public enum PlayableSource: Equatable, Sendable {
    case remote(provider: MusicProviderId)
    case local(url: URL)

    public var descriptor: PlayableSourceDescriptor {
        switch self {
        case .remote(let provider):
            return .remote(provider: provider)

        case .local(let url):
            return .local(url: url)
        }
    }
}

public struct PlaybackQueueItem: Equatable, Sendable {
    public let id: String
    public let trackId: TrackId
    public let source: PlayableSource
    public let title: String
    public let subtitle: String
    /// Длительность трека в миллисекундах.
    public let durationMs: Int64
    public let artworkUri: String?

    public init(
        id: String,
        trackId: TrackId,
        source: PlayableSource,
        title: String,
        subtitle: String,
        durationMs: Int64,
        artworkUri: String? = nil
    ) {
        self.id = id
        self.trackId = trackId
        self.source = source
        self.title = title
        self.subtitle = subtitle
        self.durationMs = durationMs
        self.artworkUri = artworkUri
    }

    public var descriptor: PlaybackQueueItemDescriptor {
        PlaybackQueueItemDescriptor(
            id: id,
            trackId: trackId,
            source: source.descriptor
        )
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

public enum PlaybackDebugLog {
    public static let relativePath = "Library/Caches/player-debug.log"

    private static let queue = DispatchQueue(label: "com.mplayeraudio.playback-debug-log")
    private static let dateFormatter = ISO8601DateFormatter()
    private static let fileName = "player-debug.log"

    public static func reset() {
        queue.sync {
            try? FileManager.default.removeItem(at: fileURL())
        }
    }

    public static func record(
        category: String,
        message: String
    ) {
        let timestamp = dateFormatter.string(from: Date())
        let line = "\(timestamp) [\(category)] \(message)\n"

        queue.sync {
            let url = fileURL()
            let directoryURL = url.deletingLastPathComponent()
            try? FileManager.default.createDirectory(
                at: directoryURL,
                withIntermediateDirectories: true,
                attributes: nil
            )

            let data = Data(line.utf8)
            if FileManager.default.fileExists(atPath: url.path) {
                guard let handle = try? FileHandle(forWritingTo: url) else {
                    return
                }
                try? handle.seekToEnd()
                try? handle.write(contentsOf: data)
                try? handle.close()
                return
            }

            try? data.write(to: url, options: .atomic)
        }
    }

    private static func fileURL() -> URL {
        if let cachesURL = FileManager.default.urls(
            for: .cachesDirectory,
            in: .userDomainMask
        ).first {
            return cachesURL.appendingPathComponent(fileName)
        }

        return URL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent(fileName)
    }
}
