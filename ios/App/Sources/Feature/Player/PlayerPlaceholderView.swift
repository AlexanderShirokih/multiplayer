import CoreUI
import Observation
import SwiftUI

private let playerCoverSize: CGFloat = 272

struct PlayerPlaceholderView: View {
    @State private var viewModel = PlayerPlaceholderViewModel()

    var body: some View {
        PlayerPlaceholderContentView(
            state: viewModel.state,
            onAction: viewModel.send
        )
        .task {
            viewModel.start()
        }
        .onDisappear {
            viewModel.stop()
        }
    }
}

private struct PlayerPlaceholderContentView: View {
    @Environment(\.multiplayerTheme) private var theme

    let state: NowPlayingStripState
    let onAction: (NowPlayingStripAction) -> Void

    var body: some View {
        GeometryReader { proxy in
            let horizontalPadding = theme.spacing.lg
            let contentWidth = min(
                max(proxy.size.width - (horizontalPadding * 2), 0),
                420
            )

            ZStack {
                LinearGradient(
                    colors: theme.colors.backgroundGradient.colors,
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()

                MultiplayerBrandBackground()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: theme.spacing.xl) {
                        VStack(spacing: theme.spacing.xs) {
                            let subtitle = """
                            Мини-плеер вынесен в общий компонент
                            и ведёт себя как самостоятельный transport control.
                            """

                            MultiplayerText(
                                verbatim: "Сейчас играет",
                                style: theme.typography.title,
                                color: theme.colors.textPrimary,
                                alignment: .center
                            )

                            MultiplayerText(
                                verbatim: subtitle,
                                style: theme.typography.body,
                                color: theme.colors.textSecondary,
                                alignment: .center
                            )
                        }
                        .frame(maxWidth: .infinity)

                        MultiplayerVinylCover(contentDescription: state.title)
                            .frame(
                                width: min(contentWidth, playerCoverSize),
                                height: min(contentWidth, playerCoverSize)
                            )
                            .shadow(
                                color: theme.elevation.level3.color.opacity(0.5),
                                radius: theme.elevation.level3.radius,
                                x: theme.elevation.level3.offsetX,
                                y: theme.elevation.level3.offsetY
                            )

                        NowPlayingStrip(
                            state: state,
                            onAction: onAction
                        )
                        .frame(maxWidth: contentWidth)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, horizontalPadding)
                    .padding(.top, theme.spacing.xxl)
                    .padding(.bottom, theme.spacing.xxxl)
                }
                .safeAreaPadding(.top)
                .safeAreaPadding(.bottom)
            }
        }
    }
}

@Observable
@MainActor
private final class PlayerPlaceholderViewModel {
    private(set) var state: NowPlayingStripState

    private let queue = DemoTrack.sampleQueue
    private var currentTrackIndex = 0
    private var playbackTask: Task<Void, Never>?

    init() {
        let initialTrack = DemoTrack.sampleQueue[0]
        let progress = 92.0 / initialTrack.durationSeconds
        state = NowPlayingStripState(
            title: initialTrack.title,
            subtitle: initialTrack.artist,
            isPlaying: true,
            currentPositionSeconds: 92,
            durationSeconds: initialTrack.durationSeconds,
            progressFraction: progress,
            displayedProgressFraction: progress
        )
    }

    func start() {
        startPlaybackLoopIfNeeded()
    }

    func stop() {
        playbackTask?.cancel()
        playbackTask = nil
    }

    func send(_ action: NowPlayingStripAction) {
        switch action {
        case .previousTapped:
            moveToTrack(index: previousIndex)

        case .playPauseTapped:
            state.isPlaying.toggle()
            state.displayedProgressFraction = currentProgressFraction

            if state.isPlaying {
                startPlaybackLoopIfNeeded()
            } else {
                stop()
            }

        case .nextTapped:
            moveToTrack(index: nextIndex)

        case .seekStarted:
            state.isSeekInProgress = true
            state.displayedProgressFraction = currentProgressFraction

        case let .seekChanged(fraction):
            state.isSeekInProgress = true
            state.displayedProgressFraction = fraction.clamped(to: 0 ... 1)

        case let .seekFinished(fraction):
            let clampedFraction = fraction.clamped(to: 0 ... 1)
            let position = state.durationSeconds * clampedFraction
            state.currentPositionSeconds = position
            state.progressFraction = clampedFraction
            state.displayedProgressFraction = clampedFraction
            state.isSeekInProgress = false
        }
    }

    private var currentTrack: DemoTrack {
        queue[currentTrackIndex]
    }

    private var previousIndex: Int {
        currentTrackIndex == 0 ? (queue.count - 1) : (currentTrackIndex - 1)
    }

    private var nextIndex: Int {
        (currentTrackIndex + 1) % queue.count
    }

    private var currentProgressFraction: Double {
        guard state.durationSeconds > 0 else {
            return 0
        }

        return (state.currentPositionSeconds / state.durationSeconds).clamped(to: 0 ... 1)
    }

    private func moveToTrack(index: Int) {
        currentTrackIndex = index
        let track = currentTrack
        let preservesPlayback = state.isPlaying

        state = NowPlayingStripState(
            title: track.title,
            subtitle: track.artist,
            isPlaying: preservesPlayback,
            currentPositionSeconds: 0,
            durationSeconds: track.durationSeconds,
            progressFraction: 0,
            displayedProgressFraction: 0,
            controlsEnabled: true
        )

        if preservesPlayback {
            startPlaybackLoopIfNeeded()
        }
    }

    private func startPlaybackLoopIfNeeded() {
        guard state.isPlaying, playbackTask == nil else {
            return
        }

        playbackTask = Task { [weak self] in
            while let self, !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(500))
                self.advancePlayback(by: 0.5)
            }
        }
    }

    private func advancePlayback(by seconds: TimeInterval) {
        guard state.isPlaying, !state.isSeekInProgress else {
            return
        }

        let nextPosition = state.currentPositionSeconds + seconds
        if nextPosition >= state.durationSeconds {
            moveToTrack(index: nextIndex)
            return
        }

        state.currentPositionSeconds = nextPosition
        state.progressFraction = currentProgressFraction
        state.displayedProgressFraction = currentProgressFraction
    }
}

private struct DemoTrack: Equatable {
    let title: String
    let artist: String
    let durationSeconds: TimeInterval

    static let sampleQueue: [Self] = [
        Self(
            title: "Midnight Echoes",
            artist: "Neon Avenue",
            durationSeconds: 240
        ),
        Self(
            title: "Northern Lights",
            artist: "Astra Harbor",
            durationSeconds: 216
        ),
        Self(
            title: "Glass Horizons",
            artist: "Silver Pulse",
            durationSeconds: 268
        )
    ]
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}

#Preview("Light") {
    MultiplayerDesignSystem(colorScheme: .light) {
        PlayerPlaceholderView()
    }
}

#Preview("Dark") {
    MultiplayerDesignSystem(colorScheme: .dark) {
        PlayerPlaceholderView()
    }
}
