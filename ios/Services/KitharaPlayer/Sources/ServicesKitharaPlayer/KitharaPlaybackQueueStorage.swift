import CorePlayer
import Foundation
import OSLog

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
            await loadQueueItem(queue[nextIndex], autoPlay: true)
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
        playbackQueueLog.info(
            "playTrack index=\(index, privacy: .public) itemId=\(item.id, privacy: .public)"
        )
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: index,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await loadQueueItem(item, autoPlay: true)
    }

    func play() async {
        guard !playbackState.queue.isEmpty else {
            playbackQueueLog.error("play ignored because queue is empty")
            return
        }
        let index = playbackState.currentIndex ?? 0
        let item = playbackState.queue[index]
        let engineState = engine.currentState
        let queueCount = self.playbackState.queue.count
        let currentPositionMs = self.playbackState.currentPositionMs
        writePlaybackQueueTrace(
            "play requested itemId=\(item.id) currentIndex=\(index) queueCount=\(queueCount) positionMs=\(currentPositionMs) engineItemId=\(engineState.currentItemId ?? "nil") engineStatus=\(String(describing: engineState.status)) engineIsPlaying=\(engineState.isPlaying)"
        )
        playbackQueueLog.info(
            "play requested itemId=\(item.id, privacy: .public) currentIndex=\(index, privacy: .public) queueCount=\(queueCount, privacy: .public) positionMs=\(currentPositionMs, privacy: .public) engineItemId=\(engineState.currentItemId ?? "nil", privacy: .public) engineStatus=\(String(describing: engineState.status), privacy: .public) engineIsPlaying=\(engineState.isPlaying, privacy: .public)"
        )
        if canResumeCurrentItem(item) {
            writePlaybackQueueTrace("resuming current item via engine.play itemId=\(item.id)")
            playbackQueueLog.info("resuming current item via engine.play itemId=\(item.id, privacy: .public)")
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

        writePlaybackQueueTrace("resume not possible, loading queue item itemId=\(item.id)")
        playbackQueueLog.info("resume not possible, loading queue item itemId=\(item.id, privacy: .public)")
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: index,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await loadQueueItem(item, autoPlay: true)
    }

    func pause() {
        guard !playbackState.queue.isEmpty else {
            playbackQueueLog.error("pause ignored because queue is empty")
            return
        }
        let currentItemId = self.playbackState.currentItem?.id ?? "nil"
        let currentIndex = self.playbackState.currentIndex ?? -1
        let currentPositionMs = self.playbackState.currentPositionMs
        writePlaybackQueueTrace(
            "pause requested itemId=\(currentItemId) currentIndex=\(currentIndex) positionMs=\(currentPositionMs)"
        )
        playbackQueueLog.info(
            "pause requested itemId=\(currentItemId, privacy: .public) currentIndex=\(currentIndex, privacy: .public) positionMs=\(currentPositionMs, privacy: .public)"
        )
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
        await loadQueueItem(item, autoPlay: true)
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
        await loadQueueItem(item, autoPlay: true)
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

    /// Storage владеет транспортным intent (`play` / `pause`), а engine поставляет наблюдаемое
    /// состояние воспроизведения. Для публичного `PlaybackQueueState` берем из engine только
    /// подтверждённую позицию: после `pause()` snapshot Kithara ещё может коротко репортить `rate > 0`,
    /// и если слепо принять такой `isPlaying`, системный виджет мерцает обратно на «воспроизведение».
    func handleEngineState(_ engineState: AudioEngineState) {
        let storageIsPlaying = self.playbackState.isPlaying
        if storageIsPlaying != engineState.isPlaying {
            writePlaybackQueueTrace(
                "engine transport mismatch storageIsPlaying=\(storageIsPlaying) engineIsPlaying=\(engineState.isPlaying) engineStatus=\(String(describing: engineState.status)) engineItemId=\(engineState.currentItemId ?? "nil") positionMs=\(engineState.currentPositionMs)"
            )
            playbackQueueLog.info(
                "engine transport mismatch storageIsPlaying=\(storageIsPlaying, privacy: .public) engineIsPlaying=\(engineState.isPlaying, privacy: .public) engineStatus=\(String(describing: engineState.status), privacy: .public) engineItemId=\(engineState.currentItemId ?? "nil", privacy: .public) positionMs=\(engineState.currentPositionMs, privacy: .public)"
            )
        }
        guard playbackState.currentPositionMs != engineState.currentPositionMs else {
            return
        }
        playbackQueueLog.info(
            "engine position update itemId=\(engineState.currentItemId ?? "nil", privacy: .public) positionMs=\(engineState.currentPositionMs, privacy: .public) status=\(String(describing: engineState.status), privacy: .public)"
        )
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: playbackState.currentIndex,
            isPlaying: playbackState.isPlaying,
            currentPositionMs: engineState.currentPositionMs,
            controlsEnabled: playbackState.controlsEnabled
        )
        broadcast()
    }

    func handleEngineEvent(_ event: AudioEngineEvent) async {
        playbackQueueLog.info("engine event received event=\(String(describing: event), privacy: .public)")
        switch event {
        case let .playedToEnd(itemId):
            await handlePlayedToEnd(itemId: itemId)

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

    private func canResumeCurrentItem(_ item: PlaybackQueueItem) -> Bool {
        guard playbackState.currentIndex != nil else {
            playbackQueueLog.info(
                "canResumeCurrentItem=false because currentIndex is nil itemId=\(item.id, privacy: .public)"
            )
            return false
        }
        let engineState = engine.currentState
        let canResume = engineState.currentItemId == item.id && engineState.status == .readyToPlay
        writePlaybackQueueTrace(
            "canResumeCurrentItem=\(canResume) itemId=\(item.id) engineItemId=\(engineState.currentItemId ?? "nil") engineStatus=\(String(describing: engineState.status)) engineIsPlaying=\(engineState.isPlaying)"
        )
        playbackQueueLog.info(
            "canResumeCurrentItem=\(canResume, privacy: .public) itemId=\(item.id, privacy: .public) engineItemId=\(engineState.currentItemId ?? "nil", privacy: .public) engineStatus=\(String(describing: engineState.status), privacy: .public) engineIsPlaying=\(engineState.isPlaying, privacy: .public)"
        )
        return canResume
    }

    private func loadQueueItem(
        _ item: PlaybackQueueItem,
        autoPlay: Bool
    ) async {
        retriedItemIDs.remove(item.id)
        writePlaybackQueueTrace("loadQueueItem start itemId=\(item.id) autoPlay=\(autoPlay)")
        playbackQueueLog.info(
            "loadQueueItem start itemId=\(item.id, privacy: .public) autoPlay=\(autoPlay, privacy: .public)"
        )
        do {
            let url = try await resolveStreamURL(for: item)
            engine.loadTrack(
                AudioTrackRequest(
                    id: item.id,
                    url: url.absoluteString
                ),
                autoPlay: autoPlay
            )
            writePlaybackQueueTrace("loadQueueItem submitted to engine itemId=\(item.id) autoPlay=\(autoPlay)")
            playbackQueueLog.info(
                "loadQueueItem submitted to engine itemId=\(item.id, privacy: .public) autoPlay=\(autoPlay, privacy: .public)"
            )
        } catch {
            writePlaybackQueueTrace(
                "loadQueueItem failed itemId=\(item.id) error=\(String(describing: error))"
            )
            playbackQueueLog.error(
                "loadQueueItem failed itemId=\(item.id, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
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
        if shouldCache(item), let cached = urlCache[item.id], !cached.isExpired {
            return cached.url
        }
        evictExpiredURLs()
        let url = try await urlResolver.playableURL(for: item.descriptor)
        if shouldCache(item) {
            urlCache[item.id] = CachedStreamURL(
                url: url,
                resolvedAt: Date()
            )
        }
        return url
    }

    private func evictExpiredURLs() {
        urlCache = urlCache.filter { !$0.value.isExpired }
    }

    private func handlePlayedToEnd(itemId: String) async {
        guard let currentIndex = playbackState.currentIndex,
              let currentItem = playbackState.currentItem else {
            playbackQueueLog.error("handlePlayedToEnd ignored because currentIndex is nil")
            return
        }
        guard itemId == currentItem.id else {
            playbackQueueLog.info(
                "ignore playedToEnd eventItemId=\(itemId, privacy: .public) currentItemId=\(currentItem.id, privacy: .public)"
            )
            return
        }
        let nextIndex = currentIndex + 1
        guard nextIndex < playbackState.queue.count else {
            playbackQueueLog.info("queue finished at index=\(currentIndex, privacy: .public)")
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
        playbackQueueLog.info(
            "advance to next item after end nextIndex=\(nextIndex, privacy: .public) itemId=\(nextItem.id, privacy: .public)"
        )
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: nextIndex,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await loadQueueItem(nextItem, autoPlay: true)
    }

    private func handleItemFailed(itemId: String?) async {
        guard let currentItem = playbackState.currentItem,
              itemId == nil || itemId == currentItem.id else {
            let currentItemId = self.playbackState.currentItem?.id ?? "nil"
            playbackQueueLog.info(
                "ignore itemFailed event eventItemId=\(itemId ?? "nil", privacy: .public) currentItemId=\(currentItemId, privacy: .public)"
            )
            return
        }
        let alreadyRetried = self.retriedItemIDs.contains(currentItem.id)
        playbackQueueLog.error(
            "handleItemFailed currentItemId=\(currentItem.id, privacy: .public) retried=\(alreadyRetried, privacy: .public)"
        )

        if retriedItemIDs.contains(currentItem.id) || !shouldRetry(item: currentItem) {
            retriedItemIDs.remove(currentItem.id)
            urlCache.removeValue(forKey: currentItem.id)
            playbackQueueLog.error("item failure fallback to handlePlayedToEnd itemId=\(currentItem.id, privacy: .public)")
            await handlePlayedToEnd(itemId: currentItem.id)
            return
        }

        retriedItemIDs.insert(currentItem.id)
        urlCache.removeValue(forKey: currentItem.id)
        playbackQueueLog.info("retrying failed item with fresh url itemId=\(currentItem.id, privacy: .public)")

        do {
            let url = try await resolveStreamURL(for: currentItem)
            let shouldAutoPlay = self.playbackState.isPlaying
            engine.loadTrack(
                AudioTrackRequest(
                    id: currentItem.id,
                    url: url.absoluteString
                ),
                autoPlay: shouldAutoPlay
            )
            playbackQueueLog.info(
                "retry load submitted itemId=\(currentItem.id, privacy: .public) autoPlay=\(shouldAutoPlay, privacy: .public)"
            )
        } catch {
            retriedItemIDs.remove(currentItem.id)
            playbackQueueLog.error(
                "retry load failed itemId=\(currentItem.id, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
            await handlePlayedToEnd(itemId: currentItem.id)
        }
    }

    private func shouldCache(_ item: PlaybackQueueItem) -> Bool {
        if case .remote = item.source {
            return true
        }
        return false
    }

    private func shouldRetry(item: PlaybackQueueItem) -> Bool {
        if case .remote = item.source {
            return true
        }
        return false
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
private let playbackQueueLog = Logger(subsystem: "com.mplayeraudio", category: "KitharaPlaybackQueue")

private func writePlaybackQueueTrace(_ message: String) {
    PlaybackDebugLog.record(category: "KitharaPlaybackQueue", message: message)
}
