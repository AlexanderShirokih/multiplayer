import Combine
import Foundation
import Kithara

final class KitharaAudioPlaybackEngine: AudioPlaybackEngine, @unchecked Sendable {
    private let player = KitharaPlayer()
    private let stateRelay = AsyncValueRelay<AudioEngineState>(AudioEngineState())
    private let eventRelay = AsyncEventRelay<AudioEngineEvent>()
    private let lock = NSLock()

    private var itemIdMap: [String: String] = [:]
    private var currentKitharaItemId: String?
    private var lastErrorMessage: String?
    private var lastFailedItemId: String?
    private var cancellables = Set<AnyCancellable>()
    private var currentItemCancellable: AnyCancellable?

    init() {
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
        player.play()
        publishState()
    }

    func pause() {
        player.pause()
        publishState()
    }

    func seekTo(positionMs: Int64) async -> Bool {
        let seconds = max(Double(positionMs) / millisecondsPerSecond, 0)
        return await withCheckedContinuation { continuation in
            player.seek(to: seconds, callback: SeekCompletionHandler { finished in
                continuation.resume(returning: finished)
            })
        }
    }

    func loadTrack(_ request: AudioTrackRequest) {
        player.removeAllItems()
        currentItemCancellable = nil

        let item = KitharaPlayerItem(url: request.url)
        lock.withLock {
            itemIdMap = [item.id: request.id]
            currentKitharaItemId = item.id
            lastErrorMessage = nil
            lastFailedItemId = nil
        }

        observeCurrentItem(item, appItemId: request.id)
        item.load()

        do {
            try player.insert(item)
            eventRelay.yield(.currentItemChanged(itemId: request.id))
            player.play()
            publishState()
        } catch {
            let message = String(describing: error)
            storeErrorMessage(message)
            emitItemFailedOnce(itemId: request.id, reason: message)
            publishState(forcedStatus: .failed)
        }
    }

    func stop() {
        player.pause()
        player.removeAllItems()
        currentItemCancellable = nil
        lock.withLock {
            itemIdMap.removeAll()
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

    private func observeCurrentItem(
        _ item: KitharaPlayerItem,
        appItemId: String
    ) {
        currentItemCancellable = item.eventPublisher
            .sink { [weak self] event in
                self?.handleItemEvent(event, appItemId: appItemId)
            }
    }

    private func handlePlayerEvent(_ event: PlayerEvent) {
        switch event {
        case .timeChanged,
                .rateChanged,
                .statusChanged,
                .timeControlStatusChanged,
                .durationChanged:
            publishState()

        case let .currentItemChanged(itemId):
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
            storeErrorMessage(error)
            let currentItemId = currentState.currentItemId
            emitItemFailedOnce(itemId: currentItemId, reason: error)
            publishState(forcedStatus: .failed)

        case .itemDidPlayToEnd:
            publishState()
            eventRelay.yield(.playedToEnd)

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

        case let .statusChanged(status):
            guard ItemStatus(ffi: status) == .failed else {
                return
            }
            let message = "Failed to load track."
            storeErrorMessage(message)
            emitItemFailedOnce(itemId: appItemId, reason: message)
            publishState(forcedStatus: .failed)

        case let .error(error):
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
        stateRelay.yield(
            AudioEngineState(
                status: status,
                currentPositionMs: milliseconds(from: snapshot.currentTime),
                durationMs: snapshot.duration.map(milliseconds(from:)),
                currentItemId: metadata.currentKitharaItemId.flatMap { metadata.itemIdMap[$0] },
                isPlaying: snapshot.rate > 0,
                errorMessage: metadata.lastErrorMessage
            )
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
}

private struct EngineMetadata {
    let currentKitharaItemId: String?
    let itemIdMap: [String: String]
    let lastErrorMessage: String?
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
