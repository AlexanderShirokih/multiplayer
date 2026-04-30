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
        guard let nextIndex = replacement.nextIndex else {
            playbackState = replacement.state
            broadcast()
            engine.stop()
            return
        }

        playbackState = replacement.state
        broadcast()

        if replacement.shouldRebuildWindow {
            await applyWindow(
                currentIndex: nextIndex,
                autoPlay: replacement.state.isPlaying,
                startPositionMs: replacement.state.currentPositionMs
            )
            return
        }

        await reconcileWindow(currentIndex: nextIndex)
        if replacement.state.isPlaying {
            engine.play()
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
        await selectOrApplyWindow(currentIndex: index, autoPlay: true)
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
        let engineItemId = engineState.currentItemId ?? "nil"
        let engineStatus = String(describing: engineState.status)
        writePlaybackQueueTrace(
            "play itemId=\(item.id) index=\(index) count=\(queueCount) " +
            "positionMs=\(currentPositionMs) engineItemId=\(engineItemId) engineStatus=\(engineStatus)"
        )
        playbackQueueLog.info(
            "play itemId=\(item.id, privacy: .public) index=\(index, privacy: .public)"
        )
        playbackQueueLog.info(
            "play engineItemId=\(engineItemId, privacy: .public)"
        )
        if canResumeCurrentItem(item) {
            let resumePositionMs = playbackState.currentPositionMs
            writePlaybackQueueTrace(
                "resuming current item via window rebuild itemId=\(item.id) positionMs=\(resumePositionMs)"
            )
            playbackQueueLog.info(
                "resume via window rebuild itemId=\(item.id, privacy: .public)"
            )
            playbackQueueLog.info(
                "resume via window rebuild positionMs=\(resumePositionMs, privacy: .public)"
            )
            playbackState = PlaybackQueueState(
                queue: playbackState.queue,
                currentIndex: index,
                isPlaying: true,
                currentPositionMs: resumePositionMs,
                controlsEnabled: true
            )
            broadcast()
            await applyWindow(
                currentIndex: index,
                autoPlay: true,
                startPositionMs: resumePositionMs
            )
            return
        }

        writePlaybackQueueTrace("resume not possible, applying queue window itemId=\(item.id)")
        playbackQueueLog.info("resume not possible, applying queue window itemId=\(item.id, privacy: .public)")
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: index,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await selectOrApplyWindow(currentIndex: index, autoPlay: true)
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
            "pause itemId=\(currentItemId, privacy: .public) index=\(currentIndex, privacy: .public) positionMs=\(currentPositionMs, privacy: .public)"
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
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: nextIndex,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await selectOrApplyWindow(currentIndex: nextIndex, autoPlay: true)
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
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: previousIndex,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await selectOrApplyWindow(currentIndex: previousIndex, autoPlay: true)
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
            let engineStatus = String(describing: engineState.status)
            writePlaybackQueueTrace(
                "transport mismatch storagePlaying=\(storageIsPlaying) enginePlaying=\(engineState.isPlaying) engineStatus=\(engineStatus)"
            )
            playbackQueueLog.info(
                "transport mismatch storagePlaying=\(storageIsPlaying, privacy: .public) enginePlaying=\(engineState.isPlaying, privacy: .public)"
            )
        }
        guard playbackState.currentPositionMs != engineState.currentPositionMs else {
            return
        }
        playbackQueueLog.info(
            "engine position itemId=\(engineState.currentItemId ?? "nil", privacy: .public) positionMs=\(engineState.currentPositionMs, privacy: .public)"
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

        case let .currentItemChanged(itemId):
            await handleCurrentItemChanged(itemId: itemId)
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
            shouldRebuildWindow: !preservingCurrentTrack
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
        let engineItemId = engineState.currentItemId ?? "nil"
        let engineStatus = String(describing: engineState.status)
        let canResume = engineState.currentItemId == item.id && engineState.status == .readyToPlay
        writePlaybackQueueTrace(
            "canResume=\(canResume) itemId=\(item.id) engineItemId=\(engineItemId) engineStatus=\(engineStatus) engineIsPlaying=\(engineState.isPlaying)"
        )
        playbackQueueLog.info(
            "canResume=\(canResume, privacy: .public) itemId=\(item.id, privacy: .public) engineItemId=\(engineItemId, privacy: .public)"
        )
        return canResume
    }

    private func selectOrApplyWindow(
        currentIndex: Int,
        autoPlay: Bool
    ) async {
        guard playbackState.queue.indices.contains(currentIndex) else {
            return
        }
        let item = playbackState.queue[currentIndex]
        if await engine.selectInWindow(appItemId: item.id, autoPlay: autoPlay) {
            await reconcileWindow(currentIndex: currentIndex)
            return
        }
        await applyWindow(currentIndex: currentIndex, autoPlay: autoPlay)
    }

    private func applyWindow(
        currentIndex: Int,
        autoPlay: Bool,
        startPositionMs: Int64 = 0
    ) async {
        guard playbackState.queue.indices.contains(currentIndex) else {
            return
        }
        let item = playbackState.queue[currentIndex]
        retriedItemIDs.remove(item.id)
        writePlaybackQueueTrace("applyWindow start itemId=\(item.id) index=\(currentIndex) autoPlay=\(autoPlay)")
        playbackQueueLog.info(
            "applyWindow start itemId=\(item.id, privacy: .public) index=\(currentIndex, privacy: .public) autoPlay=\(autoPlay, privacy: .public)"
        )
        do {
            let currentRequest = try await resolveTrackRequest(for: item)
            let nextRequest = await resolveNextTrackRequest(after: currentIndex)
            engine.setQueueWindow(
                current: currentRequest,
                next: nextRequest,
                autoPlay: autoPlay
            )
            if startPositionMs > 0 {
                _ = await engine.seekTo(positionMs: startPositionMs)
            }
            writePlaybackQueueTrace("applyWindow submitted to engine itemId=\(item.id) autoPlay=\(autoPlay)")
            playbackQueueLog.info(
                "applyWindow submitted to engine itemId=\(item.id, privacy: .public) autoPlay=\(autoPlay, privacy: .public)"
            )
        } catch {
            writePlaybackQueueTrace(
                "applyWindow failed itemId=\(item.id) error=\(String(describing: error))"
            )
            playbackQueueLog.error(
                "applyWindow failed itemId=\(item.id, privacy: .public) error=\(String(describing: error), privacy: .public)"
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

    private func reconcileWindow(currentIndex: Int) async {
        engine.pruneWindow(keepAppItemIds: keepWindowItemIds(currentIndex: currentIndex))
        await extendWindowIfNeeded(currentIndex: currentIndex)
    }

    private func extendWindowIfNeeded(currentIndex: Int) async {
        let nextIndex = currentIndex + 1
        guard playbackState.queue.indices.contains(nextIndex) else {
            return
        }
        let nextItem = playbackState.queue[nextIndex]
        guard engine.currentState.currentItemId != nextItem.id else {
            return
        }
        guard let request = await resolveOptionalTrackRequest(for: nextItem) else {
            return
        }
        engine.appendNext(request)
    }

    private func keepWindowItemIds(currentIndex: Int) -> Set<String> {
        var ids = Set<String>()
        if playbackState.queue.indices.contains(currentIndex) {
            ids.insert(playbackState.queue[currentIndex].id)
        }
        let nextIndex = currentIndex + 1
        if playbackState.queue.indices.contains(nextIndex) {
            ids.insert(playbackState.queue[nextIndex].id)
        }
        return ids
    }

    private func resolveTrackRequest(for item: PlaybackQueueItem) async throws -> AudioTrackRequest {
        let url = try await resolveStreamURL(for: item)
        return AudioTrackRequest(
            id: item.id,
            url: url.absoluteString
        )
    }

    private func resolveNextTrackRequest(after currentIndex: Int) async -> AudioTrackRequest? {
        let nextIndex = currentIndex + 1
        guard playbackState.queue.indices.contains(nextIndex) else {
            return nil
        }
        return await resolveOptionalTrackRequest(for: playbackState.queue[nextIndex])
    }

    private func resolveOptionalTrackRequest(for item: PlaybackQueueItem) async -> AudioTrackRequest? {
        do {
            return try await resolveTrackRequest(for: item)
        } catch {
            playbackQueueLog.info(
                "skip preloaded next because url resolution failed itemId=\(item.id, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
            return nil
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
        guard currentIndex == playbackState.queue.count - 1 else {
            playbackQueueLog.info(
                "mid-queue playedToEnd does not advance eventItemId=\(itemId, privacy: .public) currentIndex=\(currentIndex, privacy: .public)"
            )
            return
        }

        playbackQueueLog.info("queue finished at index=\(currentIndex, privacy: .public)")
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: playbackState.currentIndex,
            isPlaying: false,
            currentPositionMs: 0,
            controlsEnabled: playbackState.controlsEnabled
        )
        broadcast()
    }

    private func handleCurrentItemChanged(itemId: String?) async {
        guard let itemId,
              let newIndex = playbackState.queue.firstIndex(where: { $0.id == itemId }) else {
            playbackQueueLog.info(
                "ignore currentItemChanged eventItemId=\(itemId ?? "nil", privacy: .public)"
            )
            return
        }
        let previousIndex = playbackState.currentIndex
        if previousIndex != newIndex {
            playbackQueueLog.info(
                "currentItemChanged previousIndex=\(previousIndex ?? -1, privacy: .public) newIndex=\(newIndex, privacy: .public)"
            )
            playbackState = PlaybackQueueState(
                queue: playbackState.queue,
                currentIndex: newIndex,
                isPlaying: playbackState.isPlaying || engine.currentState.isPlaying,
                currentPositionMs: 0,
                controlsEnabled: true
            )
            broadcast()
        }
        await reconcileWindow(currentIndex: newIndex)
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
            playbackQueueLog.error("item failure fallback to next item itemId=\(currentItem.id, privacy: .public)")
            await advanceAfterCurrentFailure()
            return
        }

        retriedItemIDs.insert(currentItem.id)
        urlCache.removeValue(forKey: currentItem.id)
        playbackQueueLog.info("retrying failed item with fresh url itemId=\(currentItem.id, privacy: .public)")

        do {
            let request = try await resolveTrackRequest(for: currentItem)
            let shouldAutoPlay = self.playbackState.isPlaying
            let nextRequest = await resolveNextTrackRequest(after: playbackState.currentIndex ?? 0)
            engine.setQueueWindow(
                current: request,
                next: nextRequest,
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
            await advanceAfterCurrentFailure()
        }
    }

    private func advanceAfterCurrentFailure() async {
        guard let currentIndex = playbackState.currentIndex else {
            return
        }
        let nextIndex = currentIndex + 1
        guard playbackState.queue.indices.contains(nextIndex) else {
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
        playbackState = PlaybackQueueState(
            queue: playbackState.queue,
            currentIndex: nextIndex,
            isPlaying: true,
            currentPositionMs: 0,
            controlsEnabled: true
        )
        broadcast()
        await selectOrApplyWindow(currentIndex: nextIndex, autoPlay: true)
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
    let shouldRebuildWindow: Bool
}

let restartThresholdMs: Int64 = 3_000
let urlTTLSeconds: TimeInterval = 25 * 60
private let playbackQueueLog = Logger(subsystem: "com.mplayeraudio", category: "KitharaPlaybackQueue")

private func writePlaybackQueueTrace(_ message: String) {
    PlaybackDebugLog.record(category: "KitharaPlaybackQueue", message: message)
}
