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
            urlProvider: FakeTrackStreamURLProvider(
                urls: [
                    TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                    TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!
                ]
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
            urlProvider: FakeTrackStreamURLProvider(
                urls: [
                    TrackId(rawValue: "first"): URL(string: "https://example.com/first.mp3")!,
                    TrackId(rawValue: "second"): URL(string: "https://example.com/second.mp3")!
                ]
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
        engine.emit(event: .playedToEnd)

        await waitUntil {
            engine.loadedRequests.count == 2
        }

        let state = await firstValue(from: bridge.playbackStateStream())
        XCTAssertEqual(state.currentIndex, 1)
        XCTAssertEqual(engine.loadedRequests.last?.id, "1:second")
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

    func loadTrack(_ request: AudioTrackRequest) {
        lock.withLock {
            loadedRequests.append(request)
        }
        updateState { state in
            state.currentItemId = request.id
            state.isPlaying = true
            state.status = .readyToPlay
        }
    }

    func stop() {
        stateRelay.yield(AudioEngineState())
    }

    func emit(event: AudioEngineEvent) {
        eventRelay.yield(event)
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
