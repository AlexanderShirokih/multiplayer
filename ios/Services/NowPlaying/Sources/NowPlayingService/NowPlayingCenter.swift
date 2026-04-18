import AVFoundation
import CorePlayer
import Foundation
import MediaPlayer

@MainActor
public final class NowPlayingCenter {
    private let playbackBridge: PlaybackQueueBridge
    private let artworkLoader: ArtworkLoading
    private let nowPlayingInfoCenter: NowPlayingInfoCenterClient
    private let remoteCommandCenter: RemoteCommandCenterClient
    private let audioSession: AudioSessionClient
    private let notificationCenter: NotificationCenter

    private var playbackObservationTask: Task<Void, Never>?
    private var stripObservationTask: Task<Void, Never>?
    private var notificationObservers: [NSObjectProtocol] = []
    private var lastPlaybackState = PlaybackQueueState()
    private var lastProgressPublishDate = Date.distantPast
    private var lastPublishedPositionMs: Int64 = 0
    private var lastPublishedIsPlaying = false
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
        stripObservationTask?.cancel()
        notificationObservers.forEach(notificationCenter.removeObserver)
        remoteCommandCenter.removeHandlers()
    }

    public func start() {
        guard !isStarted else { return }
        isStarted = true

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

        stripObservationTask = Task { [weak self] in
            guard let self else { return }
            for await stripState in playbackBridge.stripStateStream() {
                if Task.isCancelled { break }
                await self.handleStripState(stripState)
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
            self?.runWhenControlsEnabled { center in
                Task { await center.handlePlayCommand() }
            } ?? .commandFailed
        }
        remoteCommandCenter.setHandler(for: .pause) { [weak self] in
            self?.runWhenControlsEnabled { center in
                Task { await center.playbackBridge.pause() }
            } ?? .commandFailed
        }
        remoteCommandCenter.setHandler(for: .togglePlayPause) { [weak self] in
            self?.runWhenControlsEnabled { center in
                Task { await center.handleTogglePlayPauseCommand() }
            } ?? .commandFailed
        }
    }

    func bindNavigationCommands() {
        remoteCommandCenter.setHandler(for: .nextTrack) { [weak self] in
            self?.runIf(condition: { $0.canSkipNext }) { center in
                Task { await center.playbackBridge.skipNext() }
            } ?? .commandFailed
        }
        remoteCommandCenter.setHandler(for: .previousTrack) { [weak self] in
            self?.runIf(condition: { $0.canSkipPrevious }) { center in
                Task { await center.playbackBridge.skipPrevious() }
            } ?? .commandFailed
        }
    }

    func bindSeekCommand() {
        remoteCommandCenter.setChangePlaybackPositionHandler { [weak self] timeInterval in
            self?.runIf(condition: { $0.canSeek }) { center in
                let positionMs = Int64(max(timeInterval, 0) * Double(millisecondsPerSecond))
                Task { await center.playbackBridge.seekTo(positionMs: positionMs) }
            } ?? .commandFailed
        }
    }

    /// Remote command хендлеры система вызывает синхронно на главном потоке.
    /// `MainActor.assumeIsolated` позволяет безопасно прочитать main-actor-состояние без `await`.
    func runWhenControlsEnabled(
        _ action: (NowPlayingCenter) -> Void
    ) -> MPRemoteCommandHandlerStatus {
        runIf(condition: { $0.lastPlaybackState.controlsEnabled }, action: action)
    }

    func runIf(
        condition: (NowPlayingCenter) -> Bool,
        action: (NowPlayingCenter) -> Void
    ) -> MPRemoteCommandHandlerStatus {
        MainActor.assumeIsolated {
            guard condition(self) else { return .commandFailed }
            action(self)
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
        let previousItemId = lastPlaybackState.currentItem?.id
        lastPlaybackState = playbackState
        updateCommandAvailability(for: playbackState)

        guard let currentItem = playbackState.currentItem else {
            nowPlayingInfoCenter.nowPlayingInfo = nil
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
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackQueueIndex] = playbackState.currentIndex ?? 0
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackQueueCount] = playbackState.queue.count
        nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
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

    func handleStripState(_ stripState: NowPlayingStripExternalState) async {
        if stripState.isPlaying {
            activateAudioSessionIfNeeded()
        }

        let now = Date()
        guard shouldPublishProgressUpdate(stripState, now: now) else {
            return
        }

        var nowPlayingInfo = nowPlayingInfoCenter.nowPlayingInfo ?? [:]
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = seconds(
            fromMs: stripState.currentPositionMs
        )
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = stripState.isPlaying ? 1.0 : 0.0
        if let currentItem = lastPlaybackState.currentItem {
            nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = seconds(
                fromMs: currentItem.durationMs
            )
        }
        nowPlayingInfoCenter.nowPlayingInfo = nowPlayingInfo
        lastProgressPublishDate = now
        lastPublishedPositionMs = stripState.currentPositionMs
        lastPublishedIsPlaying = stripState.isPlaying
    }

    func handlePlayCommand() async {
        activateAudioSessionIfNeeded()
        await playbackBridge.play()
    }

    func handleTogglePlayPauseCommand() async {
        if lastPlaybackState.isPlaying {
            await playbackBridge.pause()
        } else {
            await handlePlayCommand()
        }
    }

    func handleInterruption(_ notification: Notification) async {
        guard let rawValue = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let interruptionType = AVAudioSession.InterruptionType(rawValue: rawValue) else {
            return
        }

        switch interruptionType {
        case .began:
            await playbackBridge.pause()

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

        await playbackBridge.pause()
    }

    func activateAudioSessionIfNeeded() {
        guard !isAudioSessionActive else { return }
        do {
            try audioSession.configureForPlayback()
            try audioSession.setActive(true)
            isAudioSessionActive = true
        } catch {
            // Активация может не удаться при перебиваемом маршруте — повторим при следующем play.
        }
    }

    func shouldPublishProgressUpdate(
        _ stripState: NowPlayingStripExternalState,
        now: Date
    ) -> Bool {
        if nowPlayingInfoCenter.nowPlayingInfo == nil {
            return false
        }
        if stripState.isPlaying != lastPublishedIsPlaying {
            return true
        }
        if !stripState.isPlaying {
            return stripState.currentPositionMs != lastPublishedPositionMs
        }
        if now.timeIntervalSince(lastProgressPublishDate) >= progressUpdateMinIntervalSeconds {
            return true
        }
        return abs(stripState.currentPositionMs - lastPublishedPositionMs) >= progressUpdateMinDeltaMs
    }

    func updateCommandAvailability(for playbackState: PlaybackQueueState) {
        let controlsEnabled = playbackState.controlsEnabled
        remoteCommandCenter.setEnabled(controlsEnabled, for: .play)
        remoteCommandCenter.setEnabled(controlsEnabled, for: .pause)
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
}

private let millisecondsPerSecond: Int64 = 1_000
private let progressUpdateMinIntervalSeconds: TimeInterval = 0.5
private let progressUpdateMinDeltaMs: Int64 = 500
