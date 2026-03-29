import CorePlayer
import Foundation

extension PlaybackQueueStorage {
    func replaceQueue(
        queue: [PlaybackQueueItem],
        startIndex: Int?,
        autoPlay: Bool
    ) async {
        let replacement = makeQueueReplacement(
            queue: queue,
            startIndex: startIndex,
            autoPlay: autoPlay
        )
        playbackState = replacement.state
        broadcast()

        guard let nextIndex = replacement.nextIndex else {
            engine.stop()
            return
        }

        if replacement.shouldLoadTrack {
            await loadAndPlay(queue[nextIndex])
            return
        }

        if replacement.shouldStopEngine {
            engine.stop()
        }
    }

    func playTrack(index: Int) async {
        guard playbackState.queue.indices.contains(index) else {
            return
        }
        let item = playbackState.queue[index]
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: index,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await loadAndPlay(item)
    }

    func play() async {
        guard !playbackState.queue.isEmpty else {
            return
        }
        let index = playbackState.currentIndex ?? 0
        let item = playbackState.queue[index]
        if playbackState.currentIndex != nil, engine.currentState.currentItemId == item.id {
            engine.play()
            playbackState = PlaybackQueueState(
                queue: playbackState.queue,
                currentIndex: index,
                isPlaying: true,
                currentPositionMs: playbackState.currentPositionMs,
                controlsEnabled: true
            )
            broadcast()
            return
        }

        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: index,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await loadAndPlay(item)
    }

    func pause() {
        guard !playbackState.queue.isEmpty else {
            return
        }
        engine.pause()
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: playbackState.currentIndex,
            isPlaying: false,
            currentPositionMs: playbackState.currentPositionMs,
            controlsEnabled: true
        )
        broadcast()
    }

    func skipNext() async {
        guard let currentIndex = playbackState.currentIndex else {
            return
        }
        let nextIndex = min(currentIndex + 1, playbackState.queue.count - 1)
        guard nextIndex != currentIndex else {
            return
        }
        let item = playbackState.queue[nextIndex]
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: nextIndex,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await loadAndPlay(item)
    }

    func skipPrevious() async {
        guard let currentIndex = playbackState.currentIndex else {
            return
        }
        if playbackState.currentPositionMs > restartThresholdMs || currentIndex == 0 {
            let didSeek = await engine.seekTo(positionMs: 0)
            guard didSeek else { return }
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

        let previousIndex = currentIndex - 1
        let item = playbackState.queue[previousIndex]
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: previousIndex,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await loadAndPlay(item)
    }

    func seekTo(positionMs: Int64) async {
        guard let currentItem = playbackState.currentItem else {
            return
        }
        let clampedPositionMs = min(max(positionMs, 0), max(currentItem.durationMs, 0))
        let didSeek = await engine.seekTo(positionMs: clampedPositionMs)
        guard didSeek else { return }
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: playbackState.currentIndex,
            isPlaying: playbackState.isPlaying,
            currentPositionMs: clampedPositionMs,
            controlsEnabled: true
        )
        broadcast()
    }

    func handleEngineState(_ engineState: AudioEngineState) {
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: playbackState.currentIndex,
            isPlaying: engineState.isPlaying,
            currentPositionMs: engineState.currentPositionMs,
            controlsEnabled: playbackState.controlsEnabled
        )
        broadcast()
    }

    func handleEngineEvent(_ event: AudioEngineEvent) async {
        switch event {
        case .playedToEnd:
            await handlePlayedToEnd()

        case let .itemFailed(itemId, _):
            await handleItemFailed(itemId: itemId)

        case .currentItemChanged:
            break
        }
    }

    func removePlaybackContinuation(id: UUID) {
        playbackContinuations.removeValue(forKey: id)
    }

    func removeStripContinuation(id: UUID) {
        stripContinuations.removeValue(forKey: id)
    }

    private func makeQueueReplacement(
        queue: [PlaybackQueueItem],
        startIndex: Int?,
        autoPlay: Bool
    ) -> QueueReplacement {
        let previousState = playbackState
        let preservedIndex = previousState.currentItem.flatMap { currentItem in
            queue.firstIndex(where: { $0.id == currentItem.id })
        }
        let nextIndex = startIndex ?? preservedIndex
        let preservingCurrentTrack = nextIndex != nil && nextIndex == preservedIndex
        let currentPositionMs = preservedPosition(
            queue: queue,
            nextIndex: nextIndex,
            preservedIndex: preservedIndex,
            previousState: previousState
        )
        let shouldPlay = shouldPlayReplacementQueue(
            queue: queue,
            autoPlay: autoPlay,
            nextIndex: nextIndex,
            preservingCurrentTrack: preservingCurrentTrack,
            previousState: previousState
        )

        return QueueReplacement(
            state: PlaybackQueueState(
                queue: queue,
                currentIndex: nextIndex,
                isPlaying: shouldPlay,
                currentPositionMs: currentPositionMs,
                controlsEnabled: !queue.isEmpty
            ),
            nextIndex: nextIndex,
            shouldLoadTrack: shouldPlay && !preservingCurrentTrack,
            shouldStopEngine: !preservingCurrentTrack
        )
    }

    private func preservedPosition(
        queue: [PlaybackQueueItem],
        nextIndex: Int?,
        preservedIndex: Int?,
        previousState: PlaybackQueueState
    ) -> Int64 {
        guard let nextIndex,
              nextIndex == preservedIndex,
              queue.indices.contains(nextIndex) else {
            return 0
        }
        return min(
            max(previousState.currentPositionMs, 0),
            max(queue[nextIndex].durationMs, 0)
        )
    }

    private func shouldPlayReplacementQueue(
        queue: [PlaybackQueueItem],
        autoPlay: Bool,
        nextIndex: Int?,
        preservingCurrentTrack: Bool,
        previousState: PlaybackQueueState
    ) -> Bool {
        if queue.isEmpty {
            return false
        }
        if autoPlay, nextIndex != nil {
            return true
        }
        if preservingCurrentTrack {
            return previousState.isPlaying
        }
        return false
    }

    private func loadAndPlay(_ item: PlaybackQueueItem) async {
        retriedItemIDs.remove(item.id)
        do {
            let url = try await resolveStreamURL(for: item)
            engine.loadTrack(
                AudioTrackRequest(
                    id: item.id,
                    url: url.absoluteString
                )
            )
        } catch {
            playbackState = PlaybackQueueState(
                queue: playbackState.queue,
                currentIndex: playbackState.currentIndex,
                isPlaying: false,
                currentPositionMs: 0,
                controlsEnabled: playbackState.controlsEnabled
            )
            broadcast()
        }
    }

    private func resolveStreamURL(for item: PlaybackQueueItem) async throws -> URL {
        evictExpiredURLs()
        if let cached = urlCache[item.id], !cached.isExpired {
            return cached.url
        }
        let url = try await urlProvider.streamURL(for: item.trackId)
        urlCache[item.id] = CachedStreamURL(
            url: url,
            resolvedAt: Date()
        )
        return url
    }

    private func evictExpiredURLs() {
        urlCache = urlCache.filter { !$0.value.isExpired }
    }

    private func handlePlayedToEnd() async {
        guard let currentIndex = playbackState.currentIndex else {
            return
        }
        let nextIndex = currentIndex + 1
        guard nextIndex < playbackState.queue.count else {
            playbackState = PlaybackQueueState(
                queue: playbackState.queue,
                currentIndex: playbackState.currentIndex,
                isPlaying: false,
                currentPositionMs: 0,
                controlsEnabled: playbackState.controlsEnabled
            )
            broadcast()
            return
        }

        let nextItem = playbackState.queue[nextIndex]
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: nextIndex,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await loadAndPlay(nextItem)
    }

    private func handleItemFailed(itemId: String?) async {
        guard let currentItem = playbackState.currentItem,
              itemId == nil || itemId == currentItem.id else {
            return
        }

        if retriedItemIDs.contains(currentItem.id) {
            retriedItemIDs.remove(currentItem.id)
            urlCache.removeValue(forKey: currentItem.id)
            await handlePlayedToEnd()
            return
        }

        retriedItemIDs.insert(currentItem.id)
        urlCache.removeValue(forKey: currentItem.id)

        do {
            let url = try await resolveStreamURL(for: currentItem)
            engine.loadTrack(
                AudioTrackRequest(
                    id: currentItem.id,
                    url: url.absoluteString
                )
            )
        } catch {
            retriedItemIDs.remove(currentItem.id)
            await handlePlayedToEnd()
        }
    }

    private func broadcast() {
        let currentState = playbackState
        let stripState = stripState(from: currentState)
        playbackContinuations.values.forEach { $0.yield(currentState) }
        stripContinuations.values.forEach { $0.yield(stripState) }
    }

    func stripState(from playbackState: PlaybackQueueState) -> NowPlayingStripExternalState {
        let currentItem = playbackState.currentItem
        return NowPlayingStripExternalState(
            title: currentItem?.title ?? "",
            subtitle: currentItem?.subtitle ?? "",
            isPlaying: playbackState.isPlaying,
            currentPositionMs: playbackState.currentPositionMs,
            durationMs: max(currentItem?.durationMs ?? 0, 0),
            controlsEnabled: playbackState.controlsEnabled
        )
    }
}

struct CachedStreamURL {
    let url: URL
    let resolvedAt: Date

    var isExpired: Bool {
        Date().timeIntervalSince(resolvedAt) > urlTTLSeconds
    }
}

struct QueueReplacement {
    let state: PlaybackQueueState
    let nextIndex: Int?
    let shouldLoadTrack: Bool
    let shouldStopEngine: Bool
}

let restartThresholdMs: Int64 = 3_000
let urlTTLSeconds: TimeInterval = 25 * 60
