// swiftlint:disable file_length
import CoreDomain
import CorePlayer
import Foundation
import MediaPlayer
@testable import NowPlayingService
import UIKit
import XCTest

@MainActor
// swiftlint:disable:next type_body_length
final class NowPlayingCenterTests: XCTestCase {
    func testPlaybackStatePublishesNowPlayingMetadata() async {
        let playbackBridge = FakePlaybackQueueBridge()
        let artworkLoader = FakeArtworkLoader()
        let nowPlayingInfoCenter = FakeNowPlayingInfoCenter()
        let remoteCommandCenter = FakeRemoteCommandCenter()
        let audioSession = FakeAudioSession()
        let center = makeCenter(
            playbackBridge: playbackBridge,
            artworkLoader: artworkLoader,
            nowPlayingInfoCenter: nowPlayingInfoCenter,
            remoteCommandCenter: remoteCommandCenter,
            audioSession: audioSession
        )

        center.start()
        publishNowPlayingState(
            to: playbackBridge,
            artworkUri: "https://example.com/image/%%"
        )

        await waitUntil {
            (nowPlayingInfoCenter.nowPlayingInfo?[MPMediaItemPropertyTitle] as? String) == "Track"
        }

        assertMetadata(
            nowPlayingInfoCenter: nowPlayingInfoCenter,
            artworkLoader: artworkLoader,
            audioSession: audioSession,
            remoteCommandCenter: remoteCommandCenter
        )
    }

    // swiftlint:disable:next function_body_length
    func testRemoteCommandsCallPlaybackBridge() async {
        let playbackBridge = FakePlaybackQueueBridge()
        let remoteCommandCenter = FakeRemoteCommandCenter()
        let center = makeCenter(
            playbackBridge: playbackBridge,
            remoteCommandCenter: remoteCommandCenter
        )

        center.start()
        playbackBridge.yieldPlaybackState(
            queueState(
                queue: [
                    queueItem(id: "0:track"),
                    queueItem(id: "1:track")
                ],
                isPlaying: false
            )
        )
        await waitUntil {
            remoteCommandCenter.isEnabled[.play] == true
        }

        XCTAssertEqual(remoteCommandCenter.trigger(.togglePlayPause), .success)
        await waitUntil {
            playbackBridge.commands == [.play]
        }

        playbackBridge.yieldPlaybackState(
            queueState(
                queue: [
                    queueItem(id: "0:track"),
                    queueItem(id: "1:track")
                ],
                isPlaying: true
            )
        )
        await waitUntil {
            remoteCommandCenter.isEnabled[.pause] == true
        }

        XCTAssertEqual(remoteCommandCenter.trigger(.togglePlayPause), .success)
        XCTAssertEqual(remoteCommandCenter.trigger(.nextTrack), .success)
        XCTAssertEqual(remoteCommandCenter.trigger(.previousTrack), .success)
        XCTAssertEqual(remoteCommandCenter.triggerChangePlaybackPosition(64), .success)

        await waitUntil {
            playbackBridge.commands == [
                .play,
                .pause,
                .skipNext,
                .skipPrevious,
                .seekTo(positionMs: 64_000)
            ]
        }
    }

    func testPauseRemoteCommandImmediatelyPublishesPausedStateBeforeBridgeConfirmation() async {
        let playbackBridge = FakePlaybackQueueBridge()
        let nowPlayingInfoCenter = FakeNowPlayingInfoCenter()
        let remoteCommandCenter = FakeRemoteCommandCenter()
        let center = makeCenter(
            playbackBridge: playbackBridge,
            nowPlayingInfoCenter: nowPlayingInfoCenter,
            remoteCommandCenter: remoteCommandCenter
        )

        center.start()
        publishNowPlayingState(to: playbackBridge, artworkUri: nil)

        await waitUntil {
            remoteCommandCenter.isEnabled[.pause] == true
        }

        XCTAssertEqual(remoteCommandCenter.trigger(.pause), .success)

        assertPausedMetadata(nowPlayingInfoCenter: nowPlayingInfoCenter)
        XCTAssertEqual(remoteCommandCenter.isEnabled[.play], true)
        XCTAssertEqual(remoteCommandCenter.isEnabled[.pause], false)
        await waitUntil {
            playbackBridge.commands == [.pause]
        }
    }

    func testPlayRemoteCommandReactivatesAudioSessionAfterPausedPlaybackState() async {
        let playbackBridge = FakePlaybackQueueBridge()
        let remoteCommandCenter = FakeRemoteCommandCenter()
        let audioSession = FakeAudioSession()
        let center = makeCenter(
            playbackBridge: playbackBridge,
            remoteCommandCenter: remoteCommandCenter,
            audioSession: audioSession
        )

        center.start()
        publishNowPlayingState(to: playbackBridge, artworkUri: nil)

        await waitUntil {
            remoteCommandCenter.isEnabled[.pause] == true
        }
        XCTAssertEqual(audioSession.configureCallCount, 1)
        XCTAssertEqual(audioSession.setActiveCallCount, 1)

        playbackBridge.yieldPlaybackState(
            queueState(
                queue: [queueItem()],
                isPlaying: false,
                currentPositionMs: 42_000
            )
        )
        await waitUntil {
            remoteCommandCenter.isEnabled[.play] == true
        }

        XCTAssertEqual(remoteCommandCenter.trigger(.play), .success)

        await waitUntil {
            playbackBridge.commands == [.play]
        }
        XCTAssertEqual(audioSession.configureCallCount, 2)
        XCTAssertEqual(audioSession.setActiveCallCount, 2)
    }

    func testPausedPlaybackIgnoresDuplicatePlayAndToggleCommands() async {
        let playbackBridge = FakePlaybackQueueBridge()
        let remoteCommandCenter = FakeRemoteCommandCenter()
        let center = makeCenter(
            playbackBridge: playbackBridge,
            remoteCommandCenter: remoteCommandCenter
        )

        center.start()
        playbackBridge.yieldPlaybackState(
            queueState(
                queue: [queueItem()],
                isPlaying: false
            )
        )
        await waitUntil {
            remoteCommandCenter.isEnabled[.play] == true
        }

        XCTAssertEqual(remoteCommandCenter.trigger(.play), .success)
        XCTAssertEqual(remoteCommandCenter.trigger(.togglePlayPause), .success)

        await waitUntil {
            playbackBridge.commands == [.play, .play]
        }
    }

    func testPlayingPlaybackIgnoresDuplicatePauseAndToggleCommands() async {
        let playbackBridge = FakePlaybackQueueBridge()
        let remoteCommandCenter = FakeRemoteCommandCenter()
        let center = makeCenter(
            playbackBridge: playbackBridge,
            remoteCommandCenter: remoteCommandCenter
        )

        center.start()
        playbackBridge.yieldPlaybackState(
            queueState(
                queue: [queueItem()],
                isPlaying: true
            )
        )
        await waitUntil {
            remoteCommandCenter.isEnabled[.pause] == true
        }

        XCTAssertEqual(remoteCommandCenter.trigger(.pause), .success)
        XCTAssertEqual(remoteCommandCenter.trigger(.togglePlayPause), .success)

        await waitUntil {
            playbackBridge.commands == [.pause, .pause]
        }
    }

    func testPlaybackStateWithoutCurrentItemDoesNotClearPublishedMetadataForNonEmptyQueue() async {
        let playbackBridge = FakePlaybackQueueBridge()
        let nowPlayingInfoCenter = FakeNowPlayingInfoCenter()
        let center = makeCenter(
            playbackBridge: playbackBridge,
            nowPlayingInfoCenter: nowPlayingInfoCenter
        )

        center.start()
        publishNowPlayingState(
            to: playbackBridge,
            artworkUri: nil
        )

        await waitUntil {
            (nowPlayingInfoCenter.nowPlayingInfo?[MPMediaItemPropertyTitle] as? String) == "Track"
        }

        publishPausedStateWithoutCurrentItem(to: playbackBridge)
        await waitForPausedPlaybackMetadata(nowPlayingInfoCenter: nowPlayingInfoCenter)
        assertPausedMetadata(
            nowPlayingInfoCenter: nowPlayingInfoCenter
        )
    }

    func testStaleStripUpdateDoesNotOverridePausedSystemPlaybackState() async {
        let playbackBridge = FakePlaybackQueueBridge()
        let nowPlayingInfoCenter = FakeNowPlayingInfoCenter()
        let center = makeCenter(
            playbackBridge: playbackBridge,
            nowPlayingInfoCenter: nowPlayingInfoCenter
        )

        center.start()
        publishNowPlayingState(to: playbackBridge, artworkUri: nil)

        await waitUntil {
            (nowPlayingInfoCenter.nowPlayingInfo?[MPMediaItemPropertyTitle] as? String) == "Track"
        }

        playbackBridge.yieldPlaybackState(
            PlaybackQueueState(
                queue: [queueItem()],
                currentIndex: 0,
                isPlaying: false,
                currentPositionMs: 43_000,
                controlsEnabled: true
            )
        )
        playbackBridge.yieldStripState(
            NowPlayingStripExternalState(
                title: "Track",
                subtitle: "Artist",
                isPlaying: true,
                currentPositionMs: 43_500,
                durationMs: 180_000,
                controlsEnabled: true
            )
        )

        await waitForPausedPlaybackMetadata(
            nowPlayingInfoCenter: nowPlayingInfoCenter,
            elapsedPlaybackTime: 43
        )
        assertPausedMetadata(nowPlayingInfoCenter: nowPlayingInfoCenter)
        XCTAssertEqual(
            nowPlayingInfoCenter.nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? TimeInterval,
            43
        )
    }

    private func queueItem(
        id: String = "0:track",
        artworkUri: String? = nil
    ) -> PlaybackQueueItem {
        PlaybackQueueItem(
            id: id,
            trackId: TrackId(rawValue: "track"),
            source: .remote(provider: .yandexMusic),
            title: "Track",
            subtitle: "Artist",
            durationMs: 180_000,
            artworkUri: artworkUri
        )
    }

    private func queueState(
        queue: [PlaybackQueueItem]? = nil,
        currentIndex: Int? = 0,
        isPlaying: Bool,
        currentPositionMs: Int64 = 0,
        controlsEnabled: Bool = true
    ) -> PlaybackQueueState {
        PlaybackQueueState(
            queue: queue ?? [queueItem()],
            currentIndex: currentIndex,
            isPlaying: isPlaying,
            currentPositionMs: currentPositionMs,
            controlsEnabled: controlsEnabled
        )
    }

    private func makeCenter(
        playbackBridge: FakePlaybackQueueBridge,
        artworkLoader: FakeArtworkLoader = FakeArtworkLoader(),
        nowPlayingInfoCenter: FakeNowPlayingInfoCenter = FakeNowPlayingInfoCenter(),
        remoteCommandCenter: FakeRemoteCommandCenter = FakeRemoteCommandCenter(),
        audioSession: FakeAudioSession = FakeAudioSession()
    ) -> NowPlayingCenter {
        NowPlayingCenter(
            playbackBridge: playbackBridge,
            artworkLoader: artworkLoader,
            nowPlayingInfoCenter: nowPlayingInfoCenter,
            remoteCommandCenter: remoteCommandCenter,
            audioSession: audioSession,
            notificationCenter: NotificationCenter()
        )
    }

    private func publishNowPlayingState(
        to playbackBridge: FakePlaybackQueueBridge,
        artworkUri: String?
    ) {
        playbackBridge.yieldPlaybackState(
            PlaybackQueueState(
                queue: [queueItem(artworkUri: artworkUri)],
                currentIndex: 0,
                isPlaying: true,
                currentPositionMs: 42_000,
                controlsEnabled: true
            )
        )
        playbackBridge.yieldStripState(
            NowPlayingStripExternalState(
                title: "Track",
                subtitle: "Artist",
                isPlaying: true,
                currentPositionMs: 42_000,
                durationMs: 180_000,
                controlsEnabled: true
            )
        )
    }

    private func publishPausedStateWithoutCurrentItem(
        to playbackBridge: FakePlaybackQueueBridge
    ) {
        playbackBridge.yieldPlaybackState(
            PlaybackQueueState(
                queue: [queueItem()],
                currentIndex: nil,
                isPlaying: false,
                currentPositionMs: 43_000,
                controlsEnabled: true
            )
        )
        playbackBridge.yieldStripState(
            NowPlayingStripExternalState(
                title: "",
                subtitle: "",
                isPlaying: false,
                currentPositionMs: 43_000,
                durationMs: 0,
                controlsEnabled: true
            )
        )
    }

    private func assertMetadata(
        nowPlayingInfoCenter: FakeNowPlayingInfoCenter,
        artworkLoader: FakeArtworkLoader,
        audioSession: FakeAudioSession,
        remoteCommandCenter: FakeRemoteCommandCenter
    ) {
        XCTAssertEqual(
            nowPlayingInfoCenter.nowPlayingInfo?[MPMediaItemPropertyArtist] as? String,
            "Artist"
        )
        XCTAssertEqual(
            nowPlayingInfoCenter.nowPlayingInfo?[MPMediaItemPropertyPlaybackDuration] as? TimeInterval,
            180
        )
        XCTAssertEqual(
            nowPlayingInfoCenter.nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? TimeInterval,
            42
        )
        XCTAssertEqual(nowPlayingInfoCenter.playbackState, .playing)
        XCTAssertEqual(artworkLoader.requestedArtworkURIs, ["https://example.com/image/%%"])
        XCTAssertEqual(audioSession.configureCallCount, 1)
        XCTAssertEqual(audioSession.setActiveCallCount, 1)
        XCTAssertTrue(remoteCommandCenter.isEnabled[.play] == false)
        XCTAssertTrue(remoteCommandCenter.isEnabled[.pause] == true)
        XCTAssertTrue(remoteCommandCenter.isEnabled[.changePlaybackPosition] == true)
    }

    private func assertPausedMetadata(
        nowPlayingInfoCenter: FakeNowPlayingInfoCenter
    ) {
        XCTAssertEqual(
            nowPlayingInfoCenter.nowPlayingInfo?[MPMediaItemPropertyTitle] as? String,
            "Track"
        )
        XCTAssertEqual(
            nowPlayingInfoCenter.nowPlayingInfo?[MPMediaItemPropertyArtist] as? String,
            "Artist"
        )
        XCTAssertEqual(
            nowPlayingInfoCenter.nowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] as? Double,
            0.0
        )
        XCTAssertEqual(nowPlayingInfoCenter.playbackState, .paused)
    }

    private func waitForPausedPlaybackMetadata(
        nowPlayingInfoCenter: FakeNowPlayingInfoCenter,
        elapsedPlaybackTime: TimeInterval? = nil
    ) async {
        await waitUntil {
            let isPaused =
                (nowPlayingInfoCenter.nowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] as? Double) == 0.0 &&
                nowPlayingInfoCenter.playbackState == .paused
            guard isPaused else {
                return false
            }
            guard let elapsedPlaybackTime else {
                return true
            }
            return (
                nowPlayingInfoCenter.nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] as? TimeInterval
            ) == elapsedPlaybackTime
        }
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
private final class FakePlaybackQueueBridge: PlaybackQueueBridge, @unchecked Sendable {
    enum Command: Equatable {
        case play
        case pause
        case skipNext
        case skipPrevious
        case seekTo(positionMs: Int64)
    }

    private var playbackContinuations: [UUID: AsyncStream<PlaybackQueueState>.Continuation] = [:]
    private var stripContinuations: [UUID: AsyncStream<NowPlayingStripExternalState>.Continuation] = [:]
    private var playbackState = PlaybackQueueState()
    private var stripState = NowPlayingStripExternalState()

    private(set) var commands: [Command] = []

    func playbackStateStream() -> AsyncStream<PlaybackQueueState> {
        AsyncStream { continuation in
            let id = UUID()
            playbackContinuations[id] = continuation
            continuation.yield(playbackState)
            continuation.onTermination = { @Sendable _ in
                Task { @MainActor in
                    self.playbackContinuations.removeValue(forKey: id)
                }
            }
        }
    }

    func stripStateStream() -> AsyncStream<NowPlayingStripExternalState> {
        AsyncStream { continuation in
            let id = UUID()
            stripContinuations[id] = continuation
            continuation.yield(stripState)
            continuation.onTermination = { @Sendable _ in
                Task { @MainActor in
                    self.stripContinuations.removeValue(forKey: id)
                }
            }
        }
    }

    func replaceQueue(queue: [PlaybackQueueItem], startIndex: Int?, autoPlay: Bool) async {}

    func playTrack(index: Int) async {}

    func play() async {
        commands.append(.play)
    }

    func pause() async {
        commands.append(.pause)
    }

    func skipNext() async {
        commands.append(.skipNext)
    }

    func skipPrevious() async {
        commands.append(.skipPrevious)
    }

    func seekTo(positionMs: Int64) async {
        commands.append(.seekTo(positionMs: positionMs))
    }

    func yieldPlaybackState(_ state: PlaybackQueueState) {
        playbackState = state
        playbackContinuations.values.forEach { $0.yield(state) }
    }

    func yieldStripState(_ state: NowPlayingStripExternalState) {
        stripState = state
        stripContinuations.values.forEach { $0.yield(state) }
    }
}

private final class FakeArtworkLoader: ArtworkLoading, @unchecked Sendable {
    private(set) var requestedArtworkURIs: [String] = []

    func loadArtwork(for artworkUri: String?) async -> MPMediaItemArtwork? {
        guard let artworkUri else {
            return nil
        }
        requestedArtworkURIs.append(artworkUri)
        let image = UIGraphicsImageRenderer(size: CGSize(width: 16, height: 16)).image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 16, height: 16))
        }
        return MPMediaItemArtwork(boundsSize: image.size) { _ in image }
    }
}

private final class FakeNowPlayingInfoCenter: NowPlayingInfoCenterClient {
    var nowPlayingInfo: [String: Any]?
    var playbackState: MPNowPlayingPlaybackState = .stopped
}

private final class FakeRemoteCommandCenter: RemoteCommandCenterClient {
    private var handlers: [RemoteCommand: () -> MPRemoteCommandHandlerStatus] = [:]
    private var changePlaybackPositionHandler: ((TimeInterval) -> MPRemoteCommandHandlerStatus)?

    private(set) var isEnabled: [RemoteCommand: Bool] = [:]

    func setEnabled(_ isEnabled: Bool, for command: RemoteCommand) {
        self.isEnabled[command] = isEnabled
    }

    func setHandler(
        for command: RemoteCommand,
        handler: @escaping () -> MPRemoteCommandHandlerStatus
    ) {
        handlers[command] = handler
    }

    func setChangePlaybackPositionHandler(
        _ handler: @escaping (TimeInterval) -> MPRemoteCommandHandlerStatus
    ) {
        changePlaybackPositionHandler = handler
    }

    func removeHandlers() {
        handlers.removeAll()
        changePlaybackPositionHandler = nil
    }

    func trigger(_ command: RemoteCommand) -> MPRemoteCommandHandlerStatus {
        guard isEnabled[command] ?? true else {
            return .commandFailed
        }
        return handlers[command]?() ?? .commandFailed
    }

    func triggerChangePlaybackPosition(_ timeInterval: TimeInterval) -> MPRemoteCommandHandlerStatus {
        guard isEnabled[.changePlaybackPosition] ?? true else {
            return .commandFailed
        }
        return changePlaybackPositionHandler?(timeInterval) ?? .commandFailed
    }
}

private final class FakeAudioSession: AudioSessionClient {
    private(set) var configureCallCount = 0
    private(set) var setActiveCallCount = 0

    func configureForPlayback() throws {
        configureCallCount += 1
    }

    func setActive(_ isActive: Bool) throws {
        if isActive {
            setActiveCallCount += 1
        }
    }
}
