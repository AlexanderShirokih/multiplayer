import CoreDomain
import CorePlayer
import Foundation
@testable import ServicesKitharaPlayer
import XCTest

final class KitharaPlaybackQueueBridgeTests: XCTestCase {
    func testPlayTrackLoadsResolvedURLAndUpdatesCurrentIndex() async {
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
            startIndex: nil,
            autoPlay: false
        )
        await bridge.playTrack(index: 1)

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertEqual(state.currentIndex, 1)
        XCTAssertTrue(state.isPlaying)
        XCTAssertEqual(engine.loadedRequests.last?.id, "1:second")
        XCTAssertEqual(
            engine.loadedRequests.last?.url,
            "https://example.com/second.mp3"
        )
    }

    func testPlayedToEndAdvancesToNextTrack() async {
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
        engine.emit(event: .playedToEnd(itemId: "0:first"))

        await waitUntil {
            engine.loadedRequests.count == 2
        }

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertEqual(state.currentIndex, 1)
        XCTAssertEqual(engine.loadedRequests.last?.id, "1:second")
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
        XCTAssertEqual(engine.loadedRequests.count, 1)
        XCTAssertEqual(engine.loadedRequests.last?.id, "1:second")
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
            engine.loadedRequests.count == 1
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

    func testPlayAfterPauseResumesLoadedTrackWithoutReloadingQueueItem() async {
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
            engine.loadedRequests.count == 1
        }

        let playCallCountBeforeResume = engine.playCallCount
        await bridge.pause()
        await bridge.play()

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertTrue(state.isPlaying)
        XCTAssertEqual(state.currentIndex, 0)
        XCTAssertEqual(engine.loadedRequests.count, 1)
        XCTAssertEqual(engine.playCallCount, playCallCountBeforeResume + 1)
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
    private(set) var playCallCount = 0

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
        updateState { state in
            state.currentPositionMs = positionMs
        }
        return true
    }

    func loadTrack(_ request: AudioTrackRequest, autoPlay: Bool) {
        lock.withLock {
            loadedRequests.append(request)
        }
        updateState { state in
            state.currentItemId = request.id
            state.isPlaying = autoPlay
            state.status = .readyToPlay
        }
    }

    func stop() {
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
