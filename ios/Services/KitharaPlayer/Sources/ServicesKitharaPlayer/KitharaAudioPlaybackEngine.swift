import Combine
import CoreDomain
import CorePlayer
import Foundation
import Kithara
import OSLog

// swiftlint:disable:next type_body_length
final class KitharaAudioPlaybackEngine: AudioPlaybackEngine, @unchecked Sendable {
    private let player: KitharaPlayer
    private let stateRelay = AsyncValueRelay<AudioEngineState>(AudioEngineState())
    private let eventRelay = AsyncEventRelay<AudioEngineEvent>()
    private let lock = NSLock()

    private var itemIdMap: [String: String] = [:]
    private var windowEntries: [String: WindowEntry] = [:]
    private var currentKitharaItemId: String?
    private var lastErrorMessage: String?
    private var lastFailedItemId: String?
    private var cancellables = Set<AnyCancellable>()

    init() {
        let player = KitharaPlayer()
        player.crossfadeDuration = 0
        self.player = player
        observePlayerEvents()
    }

    var currentState: AudioEngineState {
        stateRelay.currentValue
    }

    func engineStateStream() -> AsyncStream<AudioEngineState> {
        stateRelay.stream()
    }

    func eventStream() -> AsyncStream<AudioEngineEvent> {
        eventRelay.stream()
    }

    func play() {
        let state = currentState
        let itemId = state.currentItemId ?? "nil"
        let status = String(describing: state.status)
        writeKitharaEngineTrace(
            "engine.play itemId=\(itemId) status=\(status) isPlaying=\(state.isPlaying)"
        )
        kitharaEngineLog.info(
            "engine.play itemId=\(itemId, privacy: .public) status=\(status, privacy: .public) isPlaying=\(state.isPlaying, privacy: .public)"
        )
        player.play()
    }

    func pause() {
        let state = currentState
        let itemId = state.currentItemId ?? "nil"
        let status = String(describing: state.status)
        writeKitharaEngineTrace(
            "engine.pause itemId=\(itemId) status=\(status) isPlaying=\(state.isPlaying)"
        )
        kitharaEngineLog.info(
            "engine.pause itemId=\(itemId, privacy: .public) status=\(status, privacy: .public) isPlaying=\(state.isPlaying, privacy: .public)"
        )
        player.pause()
    }

    func seekTo(positionMs: Int64) async -> Bool {
        let seconds = max(Double(positionMs) / millisecondsPerSecond, 0)
        return await withCheckedContinuation { continuation in
            player.seek(to: seconds, callback: SeekCompletionHandler { finished in
                continuation.resume(returning: finished)
            })
        }
    }

    func setQueueWindow(current: AudioTrackRequest, next: AudioTrackRequest?, autoPlay: Bool) {
        writeKitharaEngineTrace(
            "setQueueWindow currentId=\(current.id) nextId=\(next?.id ?? "nil") autoPlay=\(autoPlay)"
        )
        kitharaEngineLog.info(
            "setQueueWindow currentId=\(current.id, privacy: .public) nextId=\(next?.id ?? "nil", privacy: .public) autoPlay=\(autoPlay, privacy: .public)"
        )
        player.removeAllItems()

        let currentItem = makeWindowItem(request: current)
        let nextItem = next.map(makeWindowItem(request:))
        lock.withLock {
            itemIdMap.removeAll()
            windowEntries.removeAll()
            registerLocked(item: currentItem, appItemId: current.id)
            if let nextItem, let next {
                registerLocked(item: nextItem, appItemId: next.id)
            }
            currentKitharaItemId = currentItem.id
            lastErrorMessage = nil
            lastFailedItemId = nil
        }

        do {
            try player.insert(currentItem)
        } catch {
            let message = String(describing: error)
            writeKitharaEngineTrace(
                "setQueueWindow insert failed currentId=\(current.id) error=\(message)"
            )
            kitharaEngineLog.error(
                "setQueueWindow insert failed currentId=\(current.id, privacy: .public) error=\(message, privacy: .public)"
            )
            storeErrorMessage(message)
            emitItemFailedOnce(itemId: current.id, reason: message)
            publishState(forcedStatus: .failed)
            return
        }

        if let nextItem, let next {
            do {
                try player.insert(nextItem, after: currentItem)
            } catch {
                writeKitharaEngineTrace(
                    "setQueueWindow next insert failed nextId=\(next.id) error=\(String(describing: error))"
                )
                kitharaEngineLog.info(
                    "setQueueWindow next insert failed nextId=\(next.id, privacy: .public) error=\(String(describing: error), privacy: .public)"
                )
                unregisterWindowItem(appItemId: next.id, kitharaItemId: nextItem.id)
            }
        }

        if autoPlay {
            writeKitharaEngineTrace("autoPlay after setQueueWindow currentId=\(current.id)")
            kitharaEngineLog.info("autoPlay after setQueueWindow currentId=\(current.id, privacy: .public)")
            player.play()
        }
        publishState()
    }

    func appendNext(_ next: AudioTrackRequest) {
        writeKitharaEngineTrace("appendNext requestId=\(next.id)")
        kitharaEngineLog.info("appendNext requestId=\(next.id, privacy: .public)")
        if lock.withLock({ windowEntries[next.id] != nil }) {
            return
        }

        let item = makeWindowItem(request: next)
        do {
            try player.insert(item, after: player.items.last)
            lock.withLock {
                registerLocked(item: item, appItemId: next.id)
            }
            publishState()
        } catch {
            writeKitharaEngineTrace(
                "appendNext insert failed requestId=\(next.id) error=\(String(describing: error))"
            )
            kitharaEngineLog.info(
                "appendNext insert failed requestId=\(next.id, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
        }
    }

    func selectInWindow(appItemId: String, autoPlay: Bool) async -> Bool {
        guard let targetItemId = lock.withLock({ windowEntries[appItemId]?.item.id }),
              let index = player.items.firstIndex(where: { $0.id == targetItemId }) else {
            return false
        }

        do {
            try player.selectItem(at: index, autoplay: autoPlay)
            lock.withLock {
                currentKitharaItemId = targetItemId
                lastErrorMessage = nil
                lastFailedItemId = nil
            }
            publishState()
            return true
        } catch {
            writeKitharaEngineTrace(
                "selectInWindow failed appItemId=\(appItemId) error=\(String(describing: error))"
            )
            kitharaEngineLog.info(
                "selectInWindow failed appItemId=\(appItemId, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
            return false
        }
    }

    func pruneWindow(keepAppItemIds: Set<String>) {
        let entriesToRemove = lock.withLock {
            windowEntries.filter { !keepAppItemIds.contains($0.key) }
        }
        guard !entriesToRemove.isEmpty else { return }

        for (appItemId, entry) in entriesToRemove {
            do {
                try player.remove(entry.item)
            } catch {
                writeKitharaEngineTrace(
                    "pruneWindow remove failed appItemId=\(appItemId) error=\(String(describing: error))"
                )
                kitharaEngineLog.info(
                    "pruneWindow remove failed appItemId=\(appItemId, privacy: .public) error=\(String(describing: error), privacy: .public)"
                )
            }
            lock.withLock {
                itemIdMap.removeValue(forKey: entry.item.id)
                windowEntries.removeValue(forKey: appItemId)
            }
        }
        publishState()
    }

    func stop() {
        let state = currentState
        writeKitharaEngineTrace(
            "engine.stop itemId=\(state.currentItemId ?? "nil") status=\(String(describing: state.status))"
        )
        kitharaEngineLog.info(
            "engine.stop itemId=\(state.currentItemId ?? "nil", privacy: .public) status=\(String(describing: state.status), privacy: .public)"
        )
        player.pause()
        player.removeAllItems()
        lock.withLock {
            itemIdMap.removeAll()
            windowEntries.removeAll()
            currentKitharaItemId = nil
            lastErrorMessage = nil
            lastFailedItemId = nil
        }
        stateRelay.yield(AudioEngineState())
    }

    private func observePlayerEvents() {
        player.eventPublisher
            .sink { [weak self] event in
                self?.handlePlayerEvent(event)
            }
            .store(in: &cancellables)
    }

    private func handlePlayerEvent(_ event: PlayerEvent) {
        switch event {
        case .timeChanged,
                .rateChanged,
                .statusChanged,
                .timeControlStatusChanged,
                .durationChanged:
            if case .timeChanged = event {
                // Too noisy for live device debugging.
            } else {
                kitharaEngineLog.info("player event=\(String(describing: event), privacy: .public)")
            }
            publishState()

        case let .currentItemChanged(itemId):
            writeKitharaEngineTrace("player current item changed kitharaItemId=\(itemId ?? "nil")")
            kitharaEngineLog.info(
                "player current item changed kitharaItemId=\(itemId ?? "nil", privacy: .public)"
            )
            lock.withLock {
                currentKitharaItemId = itemId
            }
            let appItemId = mapAppItemId(for: itemId)
            if appItemId != nil {
                lock.withLock {
                    lastFailedItemId = nil
                    lastErrorMessage = nil
                }
            }
            publishState()
            eventRelay.yield(.currentItemChanged(itemId: appItemId))

        case let .error(error):
            writeKitharaEngineTrace("player error=\(error)")
            kitharaEngineLog.error("player error=\(error, privacy: .public)")
            storeErrorMessage(error)
            let currentItemId = currentState.currentItemId
            emitItemFailedOnce(itemId: currentItemId, reason: error)
            publishState(forcedStatus: .failed)

        case let .itemDidPlayToEnd(itemId):
            writeKitharaEngineTrace("player itemDidPlayToEnd kitharaItemId=\(itemId)")
            kitharaEngineLog.info("player itemDidPlayToEnd kitharaItemId=\(itemId, privacy: .public)")
            publishState()
            guard let appItemId = mapAppItemId(for: itemId) else {
                return
            }
            eventRelay.yield(.playedToEnd(itemId: appItemId))

        case .bufferedDurationChanged,
                .volumeChanged,
                .muteChanged:
            break
        }
    }

    private func handleItemEvent(
        _ event: ItemEvent,
        appItemId: String
    ) {
        switch event {
        case .durationChanged:
            publishState()

        case .variantsDiscovered,
                .variantSelected,
                .variantApplied:
            break

        case let .statusChanged(status):
            guard ItemStatus(ffi: status) == .failed else {
                return
            }
            let message = "Failed to load track."
            kitharaEngineLog.error(
                "item event failed statusChanged appItemId=\(appItemId, privacy: .public)"
            )
            guard isCurrentItem(appItemId: appItemId) else {
                dropPreloadedFailedItem(appItemId: appItemId, reason: message)
                return
            }
            storeErrorMessage(message)
            emitItemFailedOnce(itemId: appItemId, reason: message)
            publishState(forcedStatus: .failed)

        case let .error(error):
            kitharaEngineLog.error(
                "item event error appItemId=\(appItemId, privacy: .public) error=\(error, privacy: .public)"
            )
            guard isCurrentItem(appItemId: appItemId) else {
                dropPreloadedFailedItem(appItemId: appItemId, reason: error)
                return
            }
            storeErrorMessage(error)
            emitItemFailedOnce(itemId: appItemId, reason: error)
            publishState(forcedStatus: .failed)

        case .bufferedDurationChanged:
            break
        }
    }

    private func emitItemFailedOnce(
        itemId: String?,
        reason: String
    ) {
        let shouldEmit = lock.withLock {
            guard lastFailedItemId != itemId else {
                return false
            }
            lastFailedItemId = itemId
            return true
        }
        guard shouldEmit else { return }
        kitharaEngineLog.error(
            "emit itemFailed itemId=\(itemId ?? "nil", privacy: .public) reason=\(reason, privacy: .public)"
        )
        eventRelay.yield(.itemFailed(itemId: itemId, reason: reason))
    }

    private func publishState(forcedStatus: AudioEngineStatus? = nil) {
        let snapshot = player.snapshot
        let metadata = lock.withLock {
            EngineMetadata(
                currentKitharaItemId: currentKitharaItemId,
                itemIdMap: itemIdMap,
                lastErrorMessage: lastErrorMessage
            )
        }
        let status = forcedStatus ?? PlayerStatus(ffi: snapshot.status).toAudioEngineStatus()
        let nextState = AudioEngineState(
            status: status,
            currentPositionMs: milliseconds(from: snapshot.currentTime),
            durationMs: snapshot.duration.map(milliseconds(from:)),
            currentItemId: metadata.currentKitharaItemId.flatMap { metadata.itemIdMap[$0] },
            isPlaying: snapshot.rate > 0,
            errorMessage: metadata.lastErrorMessage
        )
        let previousState = stateRelay.currentValue
        if previousState.status != nextState.status ||
            previousState.currentItemId != nextState.currentItemId ||
            previousState.isPlaying != nextState.isPlaying {
            let itemId = nextState.currentItemId ?? "nil"
            let status = String(describing: nextState.status)
            writeKitharaEngineTrace(
            "publishState itemId=\(itemId) status=\(status) isPlaying=\(nextState.isPlaying) positionMs=\(nextState.currentPositionMs)"
        )
        kitharaEngineLog.info(
                "publishState itemId=\(itemId, privacy: .public) status=\(status, privacy: .public) isPlaying=\(nextState.isPlaying, privacy: .public)"
        )
        }
        stateRelay.yield(
            nextState
        )
    }

    private func storeErrorMessage(_ message: String) {
        lock.withLock {
            lastErrorMessage = message
        }
    }

    private func mapAppItemId(for kitharaItemId: String?) -> String? {
        guard let kitharaItemId else { return nil }
        return lock.withLock {
            itemIdMap[kitharaItemId]
        }
    }

    private func makeWindowItem(request: AudioTrackRequest) -> KitharaPlayerItem {
        let item = KitharaPlayerItem(url: request.url)
        item.load()
        return item
    }

    private func registerLocked(
        item: KitharaPlayerItem,
        appItemId: String
    ) {
        itemIdMap[item.id] = appItemId
        let cancellable = item.eventPublisher
            .sink { [weak self] event in
                self?.handleItemEvent(event, appItemId: appItemId)
            }
        windowEntries[appItemId] = WindowEntry(
            item: item,
            cancellable: cancellable
        )
    }

    private func isCurrentItem(appItemId: String) -> Bool {
        lock.withLock {
            guard let currentKitharaItemId else { return false }
            return itemIdMap[currentKitharaItemId] == appItemId
        }
    }

    private func dropPreloadedFailedItem(
        appItemId: String,
        reason: String
    ) {
        writeKitharaEngineTrace(
            "drop failed preloaded item appItemId=\(appItemId) reason=\(reason)"
        )
        kitharaEngineLog.info(
            "drop failed preloaded item appItemId=\(appItemId, privacy: .public) reason=\(reason, privacy: .public)"
        )
        guard let entry = lock.withLock({ windowEntries[appItemId] }) else {
            return
        }
        do {
            try player.remove(entry.item)
        } catch {
            kitharaEngineLog.info(
                "drop failed preloaded remove failed appItemId=\(appItemId, privacy: .public) error=\(String(describing: error), privacy: .public)"
            )
        }
        lock.withLock {
            unregisterLocked(appItemId: appItemId, kitharaItemId: entry.item.id)
        }
        publishState()
    }

    private func unregisterWindowItem(
        appItemId: String,
        kitharaItemId: String
    ) {
        lock.withLock {
            unregisterLocked(appItemId: appItemId, kitharaItemId: kitharaItemId)
        }
    }

    private func unregisterLocked(
        appItemId: String,
        kitharaItemId: String
    ) {
        itemIdMap.removeValue(forKey: kitharaItemId)
        windowEntries.removeValue(forKey: appItemId)
    }
}

private struct EngineMetadata {
    let currentKitharaItemId: String?
    let itemIdMap: [String: String]
    let lastErrorMessage: String?
}

private struct WindowEntry {
    let item: KitharaPlayerItem
    let cancellable: AnyCancellable
}

private let kitharaEngineLog = Logger(subsystem: "com.mplayeraudio", category: "KitharaEngine")

private func writeKitharaEngineTrace(_ message: String) {
    PlaybackDebugLog.record(category: "KitharaEngine", message: message)
}

private final class SeekCompletionHandler: SeekCallback, @unchecked Sendable {
    private let handler: (Bool) -> Void

    init(handler: @escaping (Bool) -> Void) {
        self.handler = handler
    }

    func onComplete(finished: Bool) {
        handler(finished)
    }
}

private extension PlayerStatus {
    func toAudioEngineStatus() -> AudioEngineStatus {
        switch self {
        case .unknown:
            return .idle

        case .readyToPlay:
            return .readyToPlay

        case .failed:
            return .failed
        }
    }
}

private func milliseconds(from seconds: Double?) -> Int64 {
    guard let seconds else { return 0 }
    return Int64(seconds * millisecondsPerSecond)
}

private let millisecondsPerSecond = 1_000.0
