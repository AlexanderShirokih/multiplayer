import CoreDomain
import CorePlayer
import CoreUI
import Foundation
import Observation

public enum TrackListStatus: Sendable, Equatable {
    case empty
    case privateLibrary
    case permissionDenied
    case genericError
}

@Observable
@MainActor
public final class TrackListViewModel {
    public private(set) var title: String = ""
    public private(set) var subtitle: String = ""
    public private(set) var trackRows: [MultiplayerTrackListItemState] = []
    public private(set) var isLoading: Bool = true
    public private(set) var status: TrackListStatus?
    public private(set) var nowPlayingStrip: NowPlayingStripState?
    public private(set) var activeTrackIndex: Int?

    private let destination: MusicLibraryDestination
    private let refreshPlaylist: RefreshPlaylistUseCase
    private let refreshSavedTracks: RefreshSavedTracksUseCase
    private let observePlaylist: ObservePlaylistUseCase
    private let observeSavedTracks: ObserveSavedTracksUseCase
    private let playbackBridge: PlaybackQueueBridge
    private let requestDeviceMediaAccess: RequestDeviceMediaAccessUseCase
    private let openSystemSettings: (@MainActor @Sendable () -> Void)?

    private var observationTasks: [Task<Void, Never>] = []
    private var externalStripState: NowPlayingStripExternalState = .init()
    private var isSeeking = false
    private var seekPreviewFraction: Double?
    private var trackItems: [TrackListItemState] = []
    private var deviceMediaAccessDenied = false

    public init(
        destination: MusicLibraryDestination,
        observePlaylist: ObservePlaylistUseCase,
        refreshPlaylist: RefreshPlaylistUseCase,
        observeSavedTracks: ObserveSavedTracksUseCase,
        refreshSavedTracks: RefreshSavedTracksUseCase,
        playbackBridge: PlaybackQueueBridge,
        requestDeviceMediaAccess: RequestDeviceMediaAccessUseCase = RequestDeviceMediaAccessUseCase(controller: nil),
        openSystemSettings: (@MainActor @Sendable () -> Void)? = nil
    ) {
        self.destination = destination
        self.observePlaylist = observePlaylist
        self.refreshPlaylist = refreshPlaylist
        self.observeSavedTracks = observeSavedTracks
        self.refreshSavedTracks = refreshSavedTracks
        self.playbackBridge = playbackBridge
        self.requestDeviceMediaAccess = requestDeviceMediaAccess
        self.openSystemSettings = openSystemSettings
        self.title = destination.title
    }
}

public extension TrackListViewModel {
    public var feedbackMessage: String {
        switch status {
        case .empty:
            return TrackListCopy.emptyMessage

        case .privateLibrary:
            return TrackListCopy.privateLibraryMessage

        case .permissionDenied:
            return TrackListCopy.permissionDeniedMessage

        case .genericError, nil:
            return TrackListCopy.loadErrorMessage
        }
    }

    func start() {
        guard observationTasks.isEmpty else { return }

        observationTasks = [
            makePlaybackObservationTask(),
            makeStripObservationTask(),
            makeLibraryObservationTask()
        ]
        refresh()
    }

    func stop() {
        observationTasks.forEach { $0.cancel() }
        observationTasks = []
    }

    func onTrackTap(index: Int) {
        Task {
            await playbackBridge.playTrack(index: index)
        }
    }

    func onNowPlayingAction(_ action: NowPlayingStripAction) {
        switch action {
        case .previousTapped:
            Task { await playbackBridge.skipPrevious() }

        case .playPauseTapped:
            Task {
                if externalStripState.isPlaying {
                    await playbackBridge.pause()
                } else {
                    await playbackBridge.play()
                }
            }

        case .nextTapped:
            Task { await playbackBridge.skipNext() }

        case .seekStarted:
            beginSeek()

        case .seekChanged(let fraction):
            updateSeekPreview(fraction: fraction)

        case .seekFinished(let fraction):
            finishSeek(fraction: fraction)
        }
    }

    func onRetry() {
        refresh()
    }

    func onFeedbackAction() {
        if status == .permissionDenied {
            openSystemSettings?()
        } else {
            onRetry()
        }
    }

    public var feedbackActionTitle: String? {
        switch status {
        case .permissionDenied:
            return TrackListCopy.openSettings

        case .empty, .privateLibrary, .genericError, nil:
            return TrackListCopy.retry
        }
    }
}

private extension TrackListViewModel {
    private func makePlaybackObservationTask() -> Task<Void, Never> {
        Task { [weak self] in
            guard let self else { return }
            for await playback in playbackBridge.playbackStateStream() {
                if Task.isCancelled {
                    break
                }
                await MainActor.run {
                    self.activeTrackIndex = playback.currentIndex
                    self.onPlaybackOrItemsChanged()
                }
            }
        }
    }

    private func makeStripObservationTask() -> Task<Void, Never> {
        Task { [weak self] in
            guard let self else { return }
            for await strip in playbackBridge.stripStateStream() {
                if Task.isCancelled {
                    break
                }
                await MainActor.run {
                    self.externalStripState = strip
                    self.rebuildNowPlayingStripState()
                }
            }
        }
    }

    private func makeLibraryObservationTask() -> Task<Void, Never> {
        switch destination.role {
        case .favourites:
            return Task { [weak self] in
                guard let self else { return }
                for await result in observeSavedTracks() {
                    if Task.isCancelled {
                        break
                    }
                    await MainActor.run {
                        self.consumeSavedTracks(result)
                    }
                }
            }

        case .regular:
            return Task { [weak self] in
                guard let self else { return }
                for await playlist in observePlaylist(ref: destination.ref) {
                    if Task.isCancelled {
                        break
                    }
                    await MainActor.run {
                        guard let playlist else { return }
                        self.consumePlaylist(playlist)
                    }
                }
            }
        }
    }

    private func beginSeek() {
        guard canSeek() else { return }
        isSeeking = true
        seekPreviewFraction = TrackListPlaybackUI.progressFraction(from: externalStripState)
        rebuildNowPlayingStripState()
    }

    private func updateSeekPreview(fraction: Double) {
        guard canSeek() else { return }
        isSeeking = true
        seekPreviewFraction = fraction
        rebuildNowPlayingStripState()
    }

    private func finishSeek(fraction: Double) {
        guard canSeek(), isSeeking else { return }
        let finalFraction = min(max(fraction, 0), 1)
        let durationMs = max(externalStripState.durationMs, 0)
        let targetMs = Int64(Double(durationMs) * finalFraction)
        Task {
            await playbackBridge.seekTo(positionMs: targetMs)
        }
        isSeeking = false
        seekPreviewFraction = nil
        rebuildNowPlayingStripState()
    }

    private func refresh() {
        Task { @MainActor in
            isLoading = true
            status = nil
            do {
                if destination.ref.provider == .device {
                    let authorizationStatus = await requestDeviceMediaAccess()
                    deviceMediaAccessDenied = authorizationStatus != .authorized
                } else {
                    deviceMediaAccessDenied = false
                }
                switch destination.role {
                case .favourites:
                    try await refreshSavedTracks()

                case .regular:
                    try await refreshPlaylist(ref: destination.ref)
                }
            } catch {
                isLoading = false
                if status == nil {
                    status = .genericError
                }
            }
        }
    }

    private func consumePlaylist(_ playlist: Playlist) {
        let items = playlist.tracks.map { $0.toTrackListItemState(provider: destination.ref.provider) }
        publishContent(title: playlist.summary.title, items: items)
    }

    private func consumeSavedTracks(_ result: SavedTracksResult) {
        switch result {
        case let .available(saved):
            publishContent(
                title: destination.title,
                items: saved.tracks.map { $0.toTrackListItemState(provider: destination.ref.provider) }
            )

        case .privateLibrary:
            Task {
                await playbackBridge.replaceQueue(queue: [], startIndex: nil, autoPlay: false)
            }
            isLoading = false
            trackItems = []
            trackRows = []
            status = .privateLibrary
            rebuildNowPlayingStripState()
        }
    }

    private func publishContent(title: String, items: [TrackListItemState]) {
        Task {
            await playbackBridge.replaceQueue(
                queue: items.map(\.queueItem),
                startIndex: nil,
                autoPlay: false
            )
        }
        trackItems = items
        subtitle = TrackListCopy.trackCountSubtitle(trackCount: items.count)
        self.title = title
        isLoading = false
        status = resolveStatus(forItemsCount: items.count)
        onPlaybackOrItemsChanged()
    }

    private func resolveStatus(forItemsCount count: Int) -> TrackListStatus? {
        if count > 0 {
            return nil
        }
        if destination.ref.provider == .device, deviceMediaAccessDenied {
            return .permissionDenied
        }
        return .empty
    }

    private func onPlaybackOrItemsChanged() {
        trackRows = trackItems.enumerated().map { index, item in
            MultiplayerTrackListItemState(
                title: item.title,
                artist: item.artist,
                duration: item.duration,
                trackPosition: item.trackPosition,
                isActive: index == activeTrackIndex
            )
        }
        rebuildNowPlayingStripState()
    }

    private func rebuildNowPlayingStripState() {
        guard activeTrackIndex != nil else {
            nowPlayingStrip = nil
            return
        }
        nowPlayingStrip = TrackListPlaybackUI.buildStripState(
            activeTrackIndex: activeTrackIndex,
            externalStripState: externalStripState,
            isSeeking: isSeeking,
            seekPreviewFraction: seekPreviewFraction
        )
    }

    private func canSeek() -> Bool {
        externalStripState.controlsEnabled && externalStripState.durationMs > 0
    }
}
