import CoreDomain
import CorePlayer
import Foundation

public final class KitharaPlaybackQueueBridge: PlaybackQueueBridge, @unchecked Sendable {
    private let storage: PlaybackQueueStorage
    private let stateObservationTask: Task<Void, Never>
    private let eventObservationTask: Task<Void, Never>

    public convenience init(urlProvider: TrackStreamUrlProvider) {
        self.init(
            engine: KitharaAudioPlaybackEngine(),
            urlProvider: urlProvider
        )
    }

    init(
        engine: AudioPlaybackEngine,
        urlProvider: TrackStreamUrlProvider
    ) {
        storage = PlaybackQueueStorage(
            engine: engine,
            urlProvider: urlProvider
        )
        stateObservationTask = Task { [storage] in
            for await engineState in engine.engineStateStream() {
                if Task.isCancelled {
                    break
                }
                await storage.handleEngineState(engineState)
            }
        }
        eventObservationTask = Task { [storage] in
            for await event in engine.eventStream() {
                if Task.isCancelled {
                    break
                }
                await storage.handleEngineEvent(event)
            }
        }
    }

    deinit {
        stateObservationTask.cancel()
        eventObservationTask.cancel()
    }

    public func playbackStateStream() -> AsyncStream<PlaybackQueueState> {
        AsyncStream { continuation in
            let id = UUID()
            Task {
                await storage.registerPlaybackContinuation(
                    id: id,
                    continuation: continuation
                )
            }
        }
    }

    public func stripStateStream() -> AsyncStream<NowPlayingStripExternalState> {
        AsyncStream { continuation in
            let id = UUID()
            Task {
                await storage.registerStripContinuation(
                    id: id,
                    continuation: continuation
                )
            }
        }
    }

    public func replaceQueue(
        queue: [PlaybackQueueItem],
        startIndex: Int?,
        autoPlay: Bool
    ) async {
        await storage.replaceQueue(
            queue: queue,
            startIndex: startIndex,
            autoPlay: autoPlay
        )
    }

    public func playTrack(index: Int) async {
        await storage.playTrack(index: index)
    }

    public func play() async {
        await storage.play()
    }

    public func pause() async {
        await storage.pause()
    }

    public func skipNext() async {
        await storage.skipNext()
    }

    public func skipPrevious() async {
        await storage.skipPrevious()
    }

    public func seekTo(positionMs: Int64) async {
        await storage.seekTo(positionMs: positionMs)
    }
}

actor PlaybackQueueStorage {
    let engine: AudioPlaybackEngine
    let urlProvider: TrackStreamUrlProvider

    var playbackState = PlaybackQueueState()
    var playbackContinuations: [UUID: AsyncStream<PlaybackQueueState>.Continuation] = [:]
    var stripContinuations: [UUID: AsyncStream<NowPlayingStripExternalState>.Continuation] = [:]
    var urlCache: [String: CachedStreamURL] = [:]
    var retriedItemIDs = Set<String>()

    init(
        engine: AudioPlaybackEngine,
        urlProvider: TrackStreamUrlProvider
    ) {
        self.engine = engine
        self.urlProvider = urlProvider
    }

    func registerPlaybackContinuation(
        id: UUID,
        continuation: AsyncStream<PlaybackQueueState>.Continuation
    ) {
        playbackContinuations[id] = continuation
        continuation.yield(playbackState)
        continuation.onTermination = { @Sendable _ in
            Task { await self.removePlaybackContinuation(id: id) }
        }
    }

    func registerStripContinuation(
        id: UUID,
        continuation: AsyncStream<NowPlayingStripExternalState>.Continuation
    ) {
        stripContinuations[id] = continuation
        continuation.yield(stripState(from: playbackState))
        continuation.onTermination = { @Sendable _ in
            Task { await self.removeStripContinuation(id: id) }
        }
    }
}
