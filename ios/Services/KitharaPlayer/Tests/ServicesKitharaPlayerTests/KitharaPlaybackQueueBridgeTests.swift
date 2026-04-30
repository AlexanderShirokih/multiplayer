import CoreDomain
import CorePlayer
import Foundation
@testable import ServicesKitharaPlayer
import XCTest

final class KitharaPlaybackQueueBridgeTests: XCTestCase {
    func testReplaceQueueAutoPlaySetsCurrentAndNextWindow() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                urls: [
                    TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                    TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!
                ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "1:second", trackId: "second")
            ],
            startIndex: 0,
            autoPlay: true
        )

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertEqual(state.currentIndex, 0)
        XCTAssertTrue(state.isPlaying)
        XCTAssertEqual(engine.setQueueWindows.last?.current.id, "0:first")
        XCTAssertEqual(engine.setQueueWindows.last?.next?.id, "1:second")
        XCTAssertEqual(engine.setQueueWindows.last?.autoPlay, true)
        XCTAssertEqual(
            engine.setQueueWindows.last?.current.url,
            "https://example.com/first.mp3"
        )
        XCTAssertEqual(
            engine.setQueueWindows.last?.next?.url,
            "https://example.com/second.mp3"
        )
    }

    func testPlayTrackOutsideWindowFallsBackToSetQueueWindow() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                urls: [
                    TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                    TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!,
                    TrackId(rawValue: "third"): URL(string: "https://example.com/third.mp3")!
                ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "1:second", trackId: "second"),
                makeQueueItem(id: "2:third", trackId: "third")
            ],
            startIndex: 0,
            autoPlay: true
        )
        await bridge.playTrack(index: 2)

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertEqual(state.currentIndex, 2)
        XCTAssertEqual(engine.selectedItemIds, ["2:third"])
        XCTAssertEqual(engine.setQueueWindows.count, 2)
        XCTAssertEqual(engine.setQueueWindows.last?.current.id, "2:third")
    }

    func testCurrentItemChangedAdvancesQueueWithoutRebuild() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                urls: [
                    TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                    TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!,
                    TrackId(rawValue: "third"): URL(string: "https://example.com/third.mp3")!
                ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "1:second", trackId: "second"),
                makeQueueItem(id: "2:third", trackId: "third")
            ],
            startIndex: 0,
            autoPlay: true
        )
        let setWindowCount = engine.setQueueWindows.count
        engine.emit(event: .currentItemChanged(itemId: "1:second"))

        let state = await waitForPlaybackState(from: bridge) { $0.currentIndex == 1 }
        XCTAssertEqual(state.currentIndex, 1)
        XCTAssertEqual(engine.setQueueWindows.count, setWindowCount)
        XCTAssertEqual(engine.prunedWindows.last, Set(["1:second", "2:third"]))
        XCTAssertEqual(engine.appendNextRequests.last?.id, "2:third")
    }

    func testPlayedToEndMidQueueDoesNotReloadNext() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                    urls: [
                        TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                        TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!
                    ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "1:second", trackId: "second")
            ],
            startIndex: 0,
            autoPlay: true
        )
        let setWindowCount = engine.setQueueWindows.count
        engine.emit(event: .playedToEnd(itemId: "0:first"))

        try? await Task.sleep(nanoseconds: 50_000_000)

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertEqual(state.currentIndex, 0)
        XCTAssertEqual(engine.setQueueWindows.count, setWindowCount)
        XCTAssertTrue(state.isPlaying)
    }

    func testPlayedToEndForStaleItemDoesNotAdvanceQueue() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                    urls: [
                        TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                        TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!,
                        TrackId(rawValue: "third"): URL(string: "https://example.com/third.mp3")!
                    ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "1:second", trackId: "second"),
                makeQueueItem(id: "2:third", trackId: "third")
            ],
            startIndex: nil,
            autoPlay: false
        )
        await bridge.playTrack(index: 1)
        engine.emit(event: .playedToEnd(itemId: "0:first"))

        try? await Task.sleep(nanoseconds: 50_000_000)

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertEqual(state.currentIndex, 1)
        XCTAssertEqual(engine.setQueueWindows.last?.current.id, "1:second")
    }

    func testPlayedToEndLastItemEndsQueue() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                    urls: [
                        TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!
                    ])
            )
        )

        await bridge.replaceQueue(
            queue: [makeQueueItem(id: "0:first", trackId: "first")],
            startIndex: 0,
            autoPlay: true
        )
        engine.emit(event: .playedToEnd(itemId: "0:first"))

        let state = await waitForPlaybackState(from: bridge) { !$0.isPlaying }
        XCTAssertEqual(state.currentIndex, 0)
        XCTAssertFalse(state.isPlaying)
    }

    func testReplaceQueuePreservingCurrentDoesNotRebuildEngineWindow() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                    urls: [
                        TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                        TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!,
                        TrackId(rawValue: "third"): URL(string: "https://example.com/third.mp3")!
                    ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "1:second", trackId: "second")
            ],
            startIndex: 0,
            autoPlay: true
        )
        let setWindowCount = engine.setQueueWindows.count

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "2:third", trackId: "third")
            ],
            startIndex: nil,
            autoPlay: false
        )

        XCTAssertEqual(engine.setQueueWindows.count, setWindowCount)
        XCTAssertEqual(engine.prunedWindows.last, Set(["0:first", "2:third"]))
        XCTAssertEqual(engine.appendNextRequests.last?.id, "2:third")
    }

    func testSkipNextUsesSelectInWindowWhenNextAlreadyPreloaded() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                    urls: [
                        TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                        TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!,
                        TrackId(rawValue: "third"): URL(string: "https://example.com/third.mp3")!
                    ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "1:second", trackId: "second"),
                makeQueueItem(id: "2:third", trackId: "third")
            ],
            startIndex: 0,
            autoPlay: true
        )
        let setWindowCount = engine.setQueueWindows.count

        await bridge.skipNext()

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertEqual(state.currentIndex, 1)
        XCTAssertEqual(engine.selectedItemIds, ["1:second"])
        XCTAssertEqual(engine.setQueueWindows.count, setWindowCount)
        XCTAssertEqual(engine.appendNextRequests.last?.id, "2:third")
    }

    func testPreloadedNextFailureDoesNotFailPlaybackState() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                    urls: [
                        TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                        TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!
                    ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "1:second", trackId: "second")
            ],
            startIndex: 0,
            autoPlay: true
        )
        engine.emit(event: .itemFailed(itemId: "1:second", reason: "next failed"))

        try? await Task.sleep(nanoseconds: 50_000_000)

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertEqual(state.currentIndex, 0)
        XCTAssertTrue(state.isPlaying)
    }

    func testCurrentItemFailureRetriesWithWindow() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                    urls: [
                        TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                        TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!
                    ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first"),
                makeQueueItem(id: "1:second", trackId: "second")
            ],
            startIndex: 0,
            autoPlay: true
        )
        let setWindowCount = engine.setQueueWindows.count
        engine.emit(event: .itemFailed(itemId: "0:first", reason: "current failed"))

        await waitUntil {
            engine.setQueueWindows.count == setWindowCount + 1
        }

        XCTAssertEqual(engine.setQueueWindows.last?.current.id, "0:first")
        XCTAssertEqual(engine.setQueueWindows.last?.next?.id, "1:second")
    }

    func testStaleEngineStateAfterPauseDoesNotResumePlayback() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                    urls: [
                        TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!
                    ])
            )
        )

        await bridge.replaceQueue(
            queue: [makeQueueItem(id: "0:first", trackId: "first")],
            startIndex: 0,
            autoPlay: true
        )
        await waitUntil {
            engine.setQueueWindows.count == 1
        }

        await bridge.pause()
        engine.emit(
            state: AudioEngineState(
                status: .readyToPlay,
                currentPositionMs: 1_000,
                currentItemId: "0:first",
                isPlaying: true
            )
        )

        let state = await waitForPlaybackState(from: bridge) { $0.currentPositionMs == 1_000 }
        XCTAssertFalse(state.isPlaying)
    }

    func testPlayAfterPauseRebuildsWindowAtCurrentPosition() async {
        let engine = FakeAudioPlaybackEngine()
        let bridge = KitharaPlaybackQueueBridge(
            engine: engine,
            urlResolver: ProviderPlayableUrlResolver(
                urlProvider: FakeTrackStreamURLProvider(
                    urls: [
                        TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!
                    ])
            )
        )

        await bridge.replaceQueue(
            queue: [
                makeQueueItem(id: "0:first", trackId: "first")
            ],
            startIndex: 0,
            autoPlay: true
        )
        await waitUntil {
            engine.setQueueWindows.count == 1
        }

        engine.emit(
            state: AudioEngineState(
                status: .readyToPlay,
                currentPositionMs: 42_000,
                currentItemId: "0:first",
                isPlaying: true
            )
        )
        _ = await waitForPlaybackState(from: bridge) { $0.currentPositionMs == 42_000 }
        await bridge.pause()
        await bridge.play()

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertTrue(state.isPlaying)
        XCTAssertEqual(state.currentIndex, 0)
        XCTAssertEqual(engine.setQueueWindows.count, 2)
        XCTAssertEqual(engine.setQueueWindows.last?.current.id, "0:first")
        XCTAssertEqual(engine.seekPositions.last, 42_000)
    }

    private func firstValue(
        from stream: AsyncStream<PlaybackQueueState>
    ) async -> PlaybackQueueState {
        var iterator = stream.makeAsyncIterator()
        guard let value = await iterator.next() else {
            XCTFail("Playback stream completed without value")
            return PlaybackQueueState()
        }
        return value
    }

    private func waitForPlaybackState(
        from bridge: KitharaPlaybackQueueBridge,
        timeoutNanoseconds: UInt64 = 1_000_000_000,
        until predicate: @escaping (PlaybackQueueState) -> Bool
    ) async -> PlaybackQueueState {
        let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
        while DispatchTime.now().uptimeNanoseconds < deadline {
            let snapshot = await firstValue(from: bridge.playbackStateStream())
            if predicate(snapshot) {
                return snapshot
            }
            await Task.yield()
        }
        XCTFail("Playback state predicate not satisfied before timeout")
        return PlaybackQueueState()
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 1_000_000_000,
        _ condition: @escaping () -> Bool
    ) async {
        let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
        while !condition() {
            if DispatchTime.now().uptimeNanoseconds >= deadline {
                XCTFail("Condition not met before timeout")
                return
            }
            await Task.yield()
        }
    }
}

private final class FakeAudioPlaybackEngine: AudioPlaybackEngine, @unchecked Sendable {
    private let stateRelay = AsyncValueRelay<AudioEngineState>(AudioEngineState())
    private let eventRelay = AsyncEventRelay<AudioEngineEvent>()
    private let lock = NSLock()
    private(set) var loadedRequests: [AudioTrackRequest] = []
    private(set) var setQueueWindows: [FakeQueueWindow] = []
    private(set) var appendNextRequests: [AudioTrackRequest] = []
    private(set) var selectedItemIds: [String] = []
    private(set) var prunedWindows: [Set<String>] = []
    private(set) var seekPositions: [Int64] = []
    private(set) var playCallCount = 0
    private var windowItemIds = Set<String>()

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
        playCallCount += 1
        updateState { state in
            state.isPlaying = true
        }
    }

    func pause() {
        updateState { state in
            state.isPlaying = false
        }
    }

    func seekTo(positionMs: Int64) async -> Bool {
        lock.withLock {
            seekPositions.append(positionMs)
        }
        updateState { state in
            state.currentPositionMs = positionMs
        }
        return true
    }

    func setQueueWindow(current: AudioTrackRequest, next: AudioTrackRequest?, autoPlay: Bool) {
        lock.withLock {
            setQueueWindows.append(
                FakeQueueWindow(
                    current: current,
                    next: next,
                    autoPlay: autoPlay
                )
            )
            loadedRequests.append(current)
            if let next {
                loadedRequests.append(next)
            }
            windowItemIds = Set([current.id] + [next?.id].compactMap { $0 })
        }
        updateState { state in
            state.currentItemId = current.id
            state.isPlaying = autoPlay
            state.status = .readyToPlay
        }
    }

    func appendNext(_ next: AudioTrackRequest) {
        lock.withLock {
            appendNextRequests.append(next)
            loadedRequests.append(next)
            windowItemIds.insert(next.id)
        }
    }

    func selectInWindow(appItemId: String, autoPlay: Bool) async -> Bool {
        lock.withLock {
            selectedItemIds.append(appItemId)
        }
        guard lock.withLock({ windowItemIds.contains(appItemId) }) else {
            return false
        }
        updateState { state in
            state.currentItemId = appItemId
            state.isPlaying = autoPlay
            state.status = .readyToPlay
            state.currentPositionMs = 0
        }
        return true
    }

    func pruneWindow(keepAppItemIds: Set<String>) {
        lock.withLock {
            prunedWindows.append(keepAppItemIds)
            windowItemIds = windowItemIds.intersection(keepAppItemIds)
        }
    }

    func stop() {
        lock.withLock {
            windowItemIds.removeAll()
        }
        stateRelay.yield(AudioEngineState())
    }

    func emit(event: AudioEngineEvent) {
        eventRelay.yield(event)
    }

    func emit(state: AudioEngineState) {
        stateRelay.yield(state)
    }

    private func updateState(_ mutate: (inout AudioEngineState) -> Void) {
        var nextState = stateRelay.currentValue
        mutate(&nextState)
        stateRelay.yield(nextState)
    }
}

private struct FakeQueueWindow {
    let current: AudioTrackRequest
    let next: AudioTrackRequest?
    let autoPlay: Bool
}

private struct FakeTrackStreamURLProvider: TrackStreamUrlProvider {
    let urls: [TrackId: URL]

    func streamURL(for trackId: TrackId) async throws -> URL {
        guard let url = urls[trackId] else {
            throw MusicLibraryError.invalidResponse(
                description: "Missing test URL for \(trackId.rawValue)"
            )
        }
        return url
    }
}

private func makeQueueItem(
    id: String,
    trackId: String
) -> PlaybackQueueItem {
    PlaybackQueueItem(
        id: id,
        trackId: TrackId(rawValue: trackId),
        source: .remote(provider: .yandexMusic),
        title: "Title \(trackId)",
        subtitle: "Artist \(trackId)",
        durationMs: 180_000
    )
}

private extension NSLock {
    func withLock<T>(_ operation: () -> T) -> T {
        lock()
        defer { unlock() }
        return operation()
    }
}
