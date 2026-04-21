import AVFoundation
import CorePlayer
import Foundation
import MediaPlayer
import OSLog

@MainActor
public final class NowPlayingCenter {
    private let playbackBridge: PlaybackQueueBridge
    private let artworkLoader: ArtworkLoading
    private let nowPlayingInfoCenter: NowPlayingInfoCenterClient
    private let remoteCommandCenter: RemoteCommandCenterClient
    private let audioSession: AudioSessionClient
    private let notificationCenter: NotificationCenter

    private var playbackObservationTask: Task<Void, Never>?
    private var notificationObservers: [NSObjectProtocol] = []
    private var lastPlaybackState = PlaybackQueueState()
    private var isStarted = false
    private var isAudioSessionActive = false

    public convenience init(
        playbackBridge: PlaybackQueueBridge,
        artworkLoader: ArtworkLoading
    ) {
        self.init(
            playbackBridge: playbackBridge,
            artworkLoader: artworkLoader,
            nowPlayingInfoCenter: SystemNowPlayingInfoCenterClient(),
            remoteCommandCenter: SystemRemoteCommandCenterClient(),
            audioSession: SystemAudioSessionClient(),
            notificationCenter: .default
        )
    }

    init(
        playbackBridge: PlaybackQueueBridge,
        artworkLoader: ArtworkLoading,
        nowPlayingInfoCenter: NowPlayingInfoCenterClient,
        remoteCommandCenter: RemoteCommandCenterClient,
        audioSession: AudioSessionClient,
        notificationCenter: NotificationCenter
    ) {
        self.playbackBridge = playbackBridge
        self.artworkLoader = artworkLoader
        self.nowPlayingInfoCenter = nowPlayingInfoCenter
        self.remoteCommandCenter = remoteCommandCenter
        self.audioSession = audioSession
        self.notificationCenter = notificationCenter
    }

    deinit {
        playbackObservationTask?.cancel()
        notificationObservers.forEach(notificationCenter.removeObserver)
        remoteCommandCenter.removeHandlers()
    }

    public func start() {
        guard !isStarted else { return }
        isStarted = true
        PlaybackDebugLog.reset()
        writeNowPlayingTrace("start now playing center")
        nowPlayingLog.info("start now playing center")

        configureRemoteCommands()
        configureNotificationObservers()
        updateCommandAvailability(for: lastPlaybackState)

        playbackObservationTask = Task { [weak self] in
            guard let self else { return }
            for await playbackState in playbackBridge.playbackStateStream() {
                if Task.isCancelled { break }
                await self.handlePlaybackState(playbackState)
            }
        }
    }
}

// MARK: - Remote commands & notifications setup

private extension NowPlayingCenter {
    func configureRemoteCommands() {
        bindPlaybackCommands()
        bindNavigationCommands()
        bindSeekCommand()
        remoteCommandCenter.setEnabled(false, for: .skipForward)
        remoteCommandCenter.setEnabled(false, for: .skipBackward)
    }

    func bindPlaybackCommands() {
        remoteCommandCenter.setHandler(for: .play) { [weak self] in
            self?.runIf(
                command: "play",
                condition: { $0.lastPlaybackState.controlsEnabled && !$0.lastPlaybackState.isPlaying },
                action: { center in
                    nowPlayingLog.info("play command accepted for itemId=\(center.lastPlaybackState.currentItem?.id ?? "nil", privacy: .public)")
                    center.applyImmediatePlaybackState(isPlaying: true)
                    Task { await center.handlePlayCommand() }
                }
            ) ?? .commandFailed
        }
        remoteCommandCenter.setHandler(for: .pause) { [weak self] in
            self?.runIf(
                command: "pause",
                condition: { $0.lastPlaybackState.controlsEnabled && $0.lastPlaybackState.isPlaying },
                action: { center in
                    nowPlayingLog.info("pause command accepted for itemId=\(center.lastPlaybackState.currentItem?.id ?? "nil", privacy: .public)")
                    center.applyImmediatePlaybackState(isPlaying: false)
                    center.deactivateAudioSessionIfNeeded()
                    Task { await center.playbackBridge.pause() }
                }
            ) ?? .commandFailed
        }
        remoteCommandCenter.setHandler(for: .togglePlayPause) { [weak self] in
            self?.runWhenControlsEnabled(command: "togglePlayPause") { center in
                let wasPlaying = center.lastPlaybackState.isPlaying
                nowPlayingLog.info("togglePlayPause command accepted wasPlaying=\(wasPlaying, privacy: .public) itemId=\(center.lastPlaybackState.currentItem?.id ?? "nil", privacy: .public)")
                if wasPlaying {
                    center.deactivateAudioSessionIfNeeded()
                }
                center.applyImmediatePlaybackState(isPlaying: !wasPlaying)
                Task { await center.handleTogglePlayPauseCommand(wasPlaying: wasPlaying) }
            } ?? .commandFailed
        }
    }

    func bindNavigationCommands() {
        remoteCommandCenter.setHandler(for: .nextTrack) { [weak self] in
            self?.runIf(
                command: "nextTrack",
                condition: { $0.canSkipNext },
                action: { center in
                    Task { await center.playbackBridge.skipNext() }
                }
            ) ?? .commandFailed
        }
        remoteCommandCenter.setHandler(for: .previousTrack) { [weak self] in
            self?.runIf(
                command: "previousTrack",
                condition: { $0.canSkipPrevious },
                action: { center in
                    Task { await center.playbackBridge.skipPrevious() }
                }
            ) ?? .commandFailed
        }
    }

    func bindSeekCommand() {
        remoteCommandCenter.setChangePlaybackPositionHandler { [weak self] timeInterval in
            self?.runIf(
                command: "changePlaybackPosition",
                condition: { $0.canSeek },
                action: { center in
                    let positionMs = Int64(max(timeInterval, 0) * Double(millisecondsPerSecond))
                    nowPlayingLog.info("changePlaybackPosition command accepted positionMs=\(positionMs, privacy: .public)")
                    Task { await center.playbackBridge.seekTo(positionMs: positionMs) }
                }
            ) ?? .commandFailed
        }
    }

    /// Remote command хендлеры система вызывает синхронно на главном потоке.
    /// `MainActor.assumeIsolated` позволяет безопасно прочитать main-actor-состояние без `await`.
    func runWhenControlsEnabled(
        command: String,
        _ action: (NowPlayingCenter) -> Void
    ) -> MPRemoteCommandHandlerStatus {
        runIf(command: command, condition: { $0.lastPlaybackState.controlsEnabled }, action: action)
    }

    func runIf(
        command: String,
        condition: (NowPlayingCenter) -> Bool,
        action: (NowPlayingCenter) -> Void
    ) -> MPRemoteCommandHandlerStatus {
        MainActor.assumeIsolated {
            let currentIndex = self.lastPlaybackState.currentIndex ?? -1
            writeNowPlayingTrace(
                "remote command received command=\(command) controlsEnabled=\(self.lastPlaybackState.controlsEnabled) isPlaying=\(self.lastPlaybackState.isPlaying) queueCount=\(self.lastPlaybackState.queue.count) currentIndex=\(currentIndex) audioSessionActive=\(self.isAudioSessionActive)"
            )
            nowPlayingLog.info(
                "remote command received command=\(command, privacy: .public) controlsEnabled=\(self.lastPlaybackState.controlsEnabled, privacy: .public) isPlaying=\(self.lastPlaybackState.isPlaying, privacy: .public) queueCount=\(self.lastPlaybackState.queue.count, privacy: .public) currentIndex=\(currentIndex, privacy: .public) audioSessionActive=\(self.isAudioSessionActive, privacy: .public)"
            )
            guard condition(self) else {
                writeNowPlayingTrace(
                    "remote command rejected command=\(command) controlsEnabled=\(self.lastPlaybackState.controlsEnabled) isPlaying=\(self.lastPlaybackState.isPlaying) currentIndex=\(currentIndex)"
                )
                nowPlayingLog.error(
                    "remote command rejected command=\(command, privacy: .public) controlsEnabled=\(self.lastPlaybackState.controlsEnabled, privacy: .public) isPlaying=\(self.lastPlaybackState.isPlaying, privacy: .public) currentIndex=\(currentIndex, privacy: .public)"
                )
                return .commandFailed
            }
            action(self)
            writeNowPlayingTrace("remote command dispatched command=\(command)")
            nowPlayingLog.info("remote command dispatched command=\(command, privacy: .public)")
            return .success
        }
    }

    func configureNotificationObservers() {
        let interruptionObserver = notificationCenter.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: nil,
            queue: nil
        ) { [weak self] notification in
            Task { [weak self] in
                await self?.handleInterruption(notification)
            }
        }
        let routeChangeObserver = notificationCenter.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: nil,
            queue: nil
        ) { [weak self] notification in
            Task { [weak self] in
                await self?.handleRouteChange(notification)
            }
        }
        notificationObservers = [interruptionObserver, routeChangeObserver]
    }
}

// MARK: - State handling

private extension NowPlayingCenter {
    func handlePlaybackState(_ playbackState: PlaybackQueueState) async {
        let previousState = lastPlaybackState
        let previousItemId = previousState.currentItem?.id
        lastPlaybackState = playbackState
        if previousState.isPlaying != playbackState.isPlaying ||
            previousState.currentIndex != playbackState.currentIndex ||
            previousItemId != playbackState.currentItem?.id ||
            previousState.controlsEnabled != playbackState.controlsEnabled {
            writeNowPlayingTrace(
                "playback state update itemId=\(playbackState.currentItem?.id ?? "nil") isPlaying=\(playbackState.isPlaying) currentIndex=\(playbackState.currentIndex ?? -1) queueCount=\(playbackState.queue.count) positionMs=\(playbackState.currentPositionMs) controlsEnabled=\(playbackState.controlsEnabled)"
            )
            nowPlayingLog.info(
                "playback state update itemId=\(playbackState.currentItem?.id ?? "nil", privacy: .public) isPlaying=\(playbackState.isPlaying, privacy: .public) currentIndex=\(playbackState.currentIndex ?? -1, privacy: .public) queueCount=\(playbackState.queue.count, privacy: .public) positionMs=\(playbackState.currentPositionMs, privacy: .public) controlsEnabled=\(playbackState.controlsEnabled, privacy: .public)"
            )
        }
        if playbackState.isPlaying {
            activateAudioSessionIfNeeded()
        } else if previousState.isPlaying {
            deactivateAudioSessionIfNeeded()
        }
        nowPlayingInfoCenter.playbackState = systemPlaybackState(for: playbackState)
        updateCommandAvailability(for: playbackState)

        guard let currentItem = playbackState.currentItem else {
            if playbackState.queue.isEmpty {
                nowPlayingInfoCenter.nowPlayingInfo = nil
            } else {
                publishTransportMetadata(for: playbackState)
            }
            return
        }

        let trackChanged = previousItemId != currentItem.id
        publishMetadata(for: currentItem, playbackState: playbackState, clearArtwork: trackChanged)
        await loadArtworkIfNeeded(for: currentItem)
    }

    func publishMetadata(
        for currentItem: PlaybackQueueItem,
        playbackState: PlaybackQueueState,
        clearArtwork: Bool
    ) {
        var nowPlayingInfo = nowPlayingInfoCenter.nowPlayingInfo ?? [:]
        if clearArtwork {
            nowPlayingInfo.removeValue(forKey: MPMediaItemPropertyArtwork)
        }
        nowPlayingInfo[MPMediaItemPropertyTitle] = currentItem.title
        nowPlayingInfo[MPMediaItemPropertyArtist] = currentItem.subtitle
        nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = seconds(fromMs: currentItem.durationMs)
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = seconds(
            fromMs: playbackState.currentPositionMs
        )
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = playbackState.isPlaying ? 1.0 : 0.0
        nowPlayingInfo[MPNowPlayingInfoPropertyDefaultPlaybackRate] = 1.0
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackQueueIndex] = playbackState.currentIndex ?? 0
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackQueueCount] = playbackState.queue.count
        nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
    }

    func publishTransportMetadata(for playbackState: PlaybackQueueState) {
        var nowPlayingInfo = nowPlayingInfoCenter.nowPlayingInfo ?? [:]
        guard !nowPlayingInfo.isEmpty else {
            return
        }
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = seconds(
            fromMs: playbackState.currentPositionMs
        )
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = playbackState.isPlaying ? 1.0 : 0.0
        nowPlayingInfo[MPNowPlayingInfoPropertyDefaultPlaybackRate] = 1.0
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackQueueIndex] = playbackState.currentIndex ?? 0
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackQueueCount] = playbackState.queue.count
        nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
    }

    func applyImmediatePlaybackState(isPlaying: Bool) {
        guard !lastPlaybackState.queue.isEmpty else {
            return
        }

        let immediatePlaybackState = PlaybackQueueState(
            queue: lastPlaybackState.queue,
            currentIndex: lastPlaybackState.currentIndex,
            isPlaying: isPlaying,
            currentPositionMs: lastPlaybackState.currentPositionMs,
            controlsEnabled: lastPlaybackState.controlsEnabled
        )
        nowPlayingLog.info(
            "apply immediate playback state itemId=\(immediatePlaybackState.currentItem?.id ?? "nil", privacy: .public) isPlaying=\(isPlaying, privacy: .public) currentIndex=\(immediatePlaybackState.currentIndex ?? -1, privacy: .public) positionMs=\(immediatePlaybackState.currentPositionMs, privacy: .public)"
        )
        nowPlayingInfoCenter.playbackState = systemPlaybackState(for: immediatePlaybackState)
        updateCommandAvailability(for: immediatePlaybackState)

        guard let currentItem = immediatePlaybackState.currentItem else {
            publishTransportMetadata(for: immediatePlaybackState)
            return
        }
        publishMetadata(
            for: currentItem,
            playbackState: immediatePlaybackState,
            clearArtwork: false
        )
    }

    func loadArtworkIfNeeded(for currentItem: PlaybackQueueItem) async {
        let currentItemId = currentItem.id
        let artwork = await artworkLoader.loadArtwork(for: currentItem.artworkUri)

        guard lastPlaybackState.currentItem?.id == currentItemId else { return }

        var updatedInfo = nowPlayingInfoCenter.nowPlayingInfo ?? [:]
        if let artwork {
            updatedInfo[MPMediaItemPropertyArtwork] = artwork
        } else {
            updatedInfo.removeValue(forKey: MPMediaItemPropertyArtwork)
        }
        nowPlayingInfoCenter.nowPlayingInfo = updatedInfo
    }

    func handlePlayCommand() async {
        let currentItemId = self.lastPlaybackState.currentItem?.id ?? "nil"
        let currentIndex = self.lastPlaybackState.currentIndex ?? -1
        let isAudioSessionActive = self.isAudioSessionActive
        writeNowPlayingTrace(
            "handle play command itemId=\(currentItemId) currentIndex=\(currentIndex) audioSessionActive=\(isAudioSessionActive)"
        )
        nowPlayingLog.info(
            "handle play command itemId=\(currentItemId, privacy: .public) currentIndex=\(currentIndex, privacy: .public) audioSessionActive=\(isAudioSessionActive, privacy: .public)"
        )
        activateAudioSessionIfNeeded()
        await playbackBridge.play()
        writeNowPlayingTrace("playbackBridge.play() finished")
        nowPlayingLog.info("playbackBridge.play() finished")
    }

    func handleTogglePlayPauseCommand(wasPlaying: Bool) async {
        nowPlayingLog.info("handle toggle play pause command wasPlaying=\(wasPlaying, privacy: .public)")
        if wasPlaying {
            await playbackBridge.pause()
            nowPlayingLog.info("playbackBridge.pause() finished from toggle")
        } else {
            await handlePlayCommand()
        }
    }

    func handleInterruption(_ notification: Notification) async {
        guard let rawValue = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let interruptionType = AVAudioSession.InterruptionType(rawValue: rawValue) else {
            return
        }
        nowPlayingLog.info("audio interruption type=\(rawValue, privacy: .public)")

        switch interruptionType {
        case .began:
            deactivateAudioSessionIfNeeded()
            await playbackBridge.pause()
            nowPlayingLog.info("paused playback due to interruption began")

        case .ended:
            let optionsRawValue = notification.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsRawValue ?? 0)
            guard options.contains(.shouldResume) else { return }
            await handlePlayCommand()

        @unknown default:
            return
        }
    }

    func handleRouteChange(_ notification: Notification) async {
        guard let rawValue = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: rawValue),
              reason == .oldDeviceUnavailable else {
            return
        }

        nowPlayingLog.info("audio route changed old device unavailable")
        deactivateAudioSessionIfNeeded()
        await playbackBridge.pause()
        nowPlayingLog.info("paused playback due to route change")
    }

    func activateAudioSessionIfNeeded() {
        guard !isAudioSessionActive else {
            writeNowPlayingTrace("skip audio session activation because it is already marked active")
            nowPlayingLog.info("skip audio session activation because it is already marked active")
            return
        }
        do {
            writeNowPlayingTrace("activating audio session")
            nowPlayingLog.info("activating audio session")
            try audioSession.configureForPlayback()
            try audioSession.setActive(true)
            isAudioSessionActive = true
            writeNowPlayingTrace("audio session activated")
            nowPlayingLog.info("audio session activated")
        } catch {
            // Активация может не удаться при перебиваемом маршруте — повторим при следующем play.
            isAudioSessionActive = false
            writeNowPlayingTrace("audio session activation failed error=\(String(describing: error))")
            nowPlayingLog.error("audio session activation failed error=\(String(describing: error), privacy: .public)")
        }
    }

    func deactivateAudioSessionIfNeeded() {
        guard isAudioSessionActive else {
            writeNowPlayingTrace("skip audio session deactivation because it is already inactive")
            nowPlayingLog.info("skip audio session deactivation because it is already inactive")
            return
        }
        do {
            writeNowPlayingTrace("deactivating audio session")
            nowPlayingLog.info("deactivating audio session")
            try audioSession.setActive(false)
            isAudioSessionActive = false
            writeNowPlayingTrace("audio session deactivated")
            nowPlayingLog.info("audio session deactivated")
        } catch {
            writeNowPlayingTrace("audio session deactivation failed error=\(String(describing: error))")
            nowPlayingLog.error("audio session deactivation failed error=\(String(describing: error), privacy: .public)")
        }
    }

    /// iOS Control Center / Lock Screen опирается на `isEnabled` каждой remote-команды,
    /// чтобы решить, какую иконку показать и какую команду диспатчить при тапе. Если оставить
    /// `pauseCommand.isEnabled = true` после паузы, система может продолжить присылать `pause`
    /// вместо `play` (см. baseline-лог в `Library/Caches/player-debug.log`). Поэтому держим
    /// взаимоисключающее состояние: `play` доступен только в paused, `pause` — только в playing,
    /// а `togglePlayPause` остаётся для headphone-кнопки и аналогичных аппаратных триггеров.
    func updateCommandAvailability(for playbackState: PlaybackQueueState) {
        let controlsEnabled = playbackState.controlsEnabled
        let isPlaying = playbackState.isPlaying
        remoteCommandCenter.setEnabled(controlsEnabled && !isPlaying, for: .play)
        remoteCommandCenter.setEnabled(controlsEnabled && isPlaying, for: .pause)
        remoteCommandCenter.setEnabled(controlsEnabled, for: .togglePlayPause)
        remoteCommandCenter.setEnabled(canSkipNext, for: .nextTrack)
        remoteCommandCenter.setEnabled(canSkipPrevious, for: .previousTrack)
        remoteCommandCenter.setEnabled(canSeek, for: .changePlaybackPosition)
    }

    var canSkipNext: Bool {
        guard lastPlaybackState.controlsEnabled,
              let currentIndex = lastPlaybackState.currentIndex else {
            return false
        }
        return currentIndex < lastPlaybackState.queue.count - 1
    }

    var canSkipPrevious: Bool {
        lastPlaybackState.controlsEnabled && lastPlaybackState.currentItem != nil
    }

    var canSeek: Bool {
        guard lastPlaybackState.controlsEnabled,
              let currentItem = lastPlaybackState.currentItem else {
            return false
        }
        return currentItem.durationMs > 0
    }

    func seconds(fromMs milliseconds: Int64) -> TimeInterval {
        TimeInterval(max(milliseconds, 0)) / Double(millisecondsPerSecond)
    }

    func systemPlaybackState(
        for playbackState: PlaybackQueueState
    ) -> MPNowPlayingPlaybackState {
        guard !playbackState.queue.isEmpty else {
            return .stopped
        }
        return playbackState.isPlaying ? .playing : .paused
    }
}

private let millisecondsPerSecond: Int64 = 1_000
private let nowPlayingLog = Logger(subsystem: "com.mplayeraudio", category: "NowPlayingCenter")

private func writeNowPlayingTrace(_ message: String) {
    PlaybackDebugLog.record(category: "NowPlayingCenter", message: message)
}
