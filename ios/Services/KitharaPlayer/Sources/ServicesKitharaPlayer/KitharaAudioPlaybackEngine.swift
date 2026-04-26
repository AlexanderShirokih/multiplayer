import Combine
import CoreDomain
import CorePlayer
import Foundation
import Kithara
import OSLog

final class KitharaAudioPlaybackEngine: AudioPlaybackEngine, @unchecked Sendable {
    private let player: KitharaPlayer
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
        writeKitharaEngineTrace(
            "engine.play itemId=\(state.currentItemId ?? "nil") status=\(String(describing: state.status)) isPlaying=\(state.isPlaying)"
        )
        kitharaEngineLog.info(
            "engine.play itemId=\(state.currentItemId ?? "nil", privacy: .public) status=\(String(describing: state.status), privacy: .public) isPlaying=\(state.isPlaying, privacy: .public)"
        )
        player.play()
    }

    func pause() {
        let state = currentState
        writeKitharaEngineTrace(
            "engine.pause itemId=\(state.currentItemId ?? "nil") status=\(String(describing: state.status)) isPlaying=\(state.isPlaying)"
        )
        kitharaEngineLog.info(
            "engine.pause itemId=\(state.currentItemId ?? "nil", privacy: .public) status=\(String(describing: state.status), privacy: .public) isPlaying=\(state.isPlaying, privacy: .public)"
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

    func loadTrack(_ request: AudioTrackRequest, autoPlay: Bool) {
        writeKitharaEngineTrace("loadTrack requestId=\(request.id) autoPlay=\(autoPlay)")
        kitharaEngineLog.info(
            "loadTrack requestId=\(request.id, privacy: .public) autoPlay=\(autoPlay, privacy: .public)"
        )
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
            if autoPlay {
                writeKitharaEngineTrace("autoPlay after loadTrack requestId=\(request.id)")
                kitharaEngineLog.info("autoPlay after loadTrack requestId=\(request.id, privacy: .public)")
                player.play()
            }
            publishState()
        } catch {
            let message = String(describing: error)
            writeKitharaEngineTrace(
                "loadTrack insert failed requestId=\(request.id) error=\(message)"
            )
            kitharaEngineLog.error(
                "loadTrack insert failed requestId=\(request.id, privacy: .public) error=\(message, privacy: .public)"
            )
            storeErrorMessage(message)
            emitItemFailedOnce(itemId: request.id, reason: message)
            publishState(forcedStatus: .failed)
        }
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

        case .itemDidPlayToEnd:
            writeKitharaEngineTrace("player itemDidPlayToEnd")
            kitharaEngineLog.info("player itemDidPlayToEnd")
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
            storeErrorMessage(message)
            emitItemFailedOnce(itemId: appItemId, reason: message)
            publishState(forcedStatus: .failed)

        case let .error(error):
            kitharaEngineLog.error(
                "item event error appItemId=\(appItemId, privacy: .public) error=\(error, privacy: .public)"
            )
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
            writeKitharaEngineTrace(
                "publishState itemId=\(nextState.currentItemId ?? "nil") status=\(String(describing: nextState.status)) isPlaying=\(nextState.isPlaying) positionMs=\(nextState.currentPositionMs)"
            )
            kitharaEngineLog.info(
                "publishState itemId=\(nextState.currentItemId ?? "nil", privacy: .public) status=\(String(describing: nextState.status), privacy: .public) isPlaying=\(nextState.isPlaying, privacy: .public) positionMs=\(nextState.currentPositionMs, privacy: .public)"
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
}

private struct EngineMetadata {
    let currentKitharaItemId: String?
    let itemIdMap: [String: String]
    let lastErrorMessage: String?
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
