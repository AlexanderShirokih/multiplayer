import CorePlayer
import Foundation

/// In-memory реализация очереди воспроизведения (параллель Android `InMemoryPlaybackQueueBridge`).
public final class InMemoryPlaybackQueueBridge: PlaybackQueueBridge, @unchecked Sendable {
    private let storage = PlaybackQueueStorage()

    public init() {}

    public func playbackStateStream() -> AsyncStream<PlaybackQueueState> {
        AsyncStream { continuation in
            let id = UUID()
            Task {
                await storage.registerPlaybackContinuation(id: id, continuation: continuation)
            }
        }
    }

    public func stripStateStream() -> AsyncStream<NowPlayingStripExternalState> {
        AsyncStream { continuation in
            let id = UUID()
            Task {
                await storage.registerStripContinuation(id: id, continuation: continuation)
            }
        }
    }

    public func replaceQueue(queue: [PlaybackQueueItem], startIndex: Int?, autoPlay: Bool) async {
        await storage.replaceQueue(queue: queue, startIndex: startIndex, autoPlay: autoPlay)
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

private actor PlaybackQueueStorage {
    private var playbackState = PlaybackQueueState()
    private var playbackContinuations: [UUID: AsyncStream<PlaybackQueueState>.Continuation] = [:]
    private var stripContinuations: [UUID: AsyncStream<NowPlayingStripExternalState>.Continuation] = [:]

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

    func removePlaybackContinuation(id: UUID) {
        playbackContinuations.removeValue(forKey: id)
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

    func removeStripContinuation(id: UUID) {
        stripContinuations.removeValue(forKey: id)
    }

    func replaceQueue(queue: [PlaybackQueueItem], startIndex: Int?, autoPlay: Bool) {
        let previousState = playbackState
        let preservedIndex = previousState.currentItem.flatMap { currentItem in
            queue.firstIndex(where: { $0.id == currentItem.id })
        }
        let nextIndex = startIndex ?? preservedIndex
        let currentPositionMs: Int64
        if let nextIndex, nextIndex == preservedIndex,
           nextIndex >= 0, nextIndex < queue.count {
            let durationMs = max(queue[nextIndex].durationMs, 0)
            currentPositionMs = min(
                max(previousState.currentPositionMs, 0),
                durationMs
            )
        } else {
            currentPositionMs = 0
        }

        let isPlaying: Bool
        if queue.isEmpty {
            isPlaying = false
        } else if autoPlay, nextIndex != nil {
            isPlaying = true
        } else if let nextIndex, nextIndex == preservedIndex {
            isPlaying = previousState.isPlaying
        } else {
            isPlaying = false
        }

        playbackState = PlaybackQueueState(
            queue: queue,
            currentIndex: nextIndex,
            isPlaying: isPlaying,
            currentPositionMs: currentPositionMs,
            controlsEnabled: !queue.isEmpty
        )
        broadcast()
    }

    func playTrack(index: Int) {
        guard playbackState.queue.indices.contains(index) else {
            return
        }
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: index,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: !playbackState.queue.isEmpty
        )
        broadcast()
    }

    func play() {
        guard !playbackState.queue.isEmpty else {
            return
        }
        let index = playbackState.currentIndex ?? 0
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: index,
            isPlaying: true,
            controlsEnabled: true
        )
        broadcast()
    }

    func pause() {
        guard !playbackState.queue.isEmpty else {
            return
        }
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: playbackState.currentIndex,
            isPlaying: false,
            currentPositionMs: playbackState.currentPositionMs,
            controlsEnabled: true
        )
        broadcast()
    }

    func skipNext() {
        guard let currentIndex = playbackState.currentIndex else {
            return
        }
        let nextIndex = min(currentIndex + 1, playbackState.queue.count - 1)
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: nextIndex,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
    }

    func skipPrevious() {
        guard let currentIndex = playbackState.currentIndex else {
            return
        }
        if playbackState.currentPositionMs > restartThresholdMs {
            playbackState = PlaybackQueueState(
                queue: playbackState.queue,
                currentIndex: currentIndex,
                isPlaying: playbackState.isPlaying,
                currentPositionMs: 0,
                controlsEnabled: true
            )
            broadcast()
            return
        }
        let previousIndex = max(currentIndex - 1, 0)
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: previousIndex,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
    }

    func seekTo(positionMs: Int64) {
        guard let currentItem = playbackState.currentItem else {
            return
        }
        let maxMs = max(currentItem.durationMs, 0)
        let clamped = min(max(positionMs, 0), maxMs)
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: playbackState.currentIndex,
            isPlaying: playbackState.isPlaying,
            currentPositionMs: clamped,
            controlsEnabled: true
        )
        broadcast()
    }

    private func broadcast() {
        let state = playbackState
        let strip = stripState(from: state)
        for continuation in playbackContinuations.values {
            continuation.yield(state)
        }
        for continuation in stripContinuations.values {
            continuation.yield(strip)
        }
    }

    private func stripState(from playback: PlaybackQueueState) -> NowPlayingStripExternalState {
        let current = playback.currentItem
        return NowPlayingStripExternalState(
            title: current?.title ?? "",
            subtitle: current?.subtitle ?? "",
            isPlaying: playback.isPlaying,
            currentPositionMs: playback.currentPositionMs,
            durationMs: current.map { max($0.durationMs, 0) } ?? 0,
            controlsEnabled: playback.controlsEnabled
        )
    }
}

private let restartThresholdMs: Int64 = 3_000
