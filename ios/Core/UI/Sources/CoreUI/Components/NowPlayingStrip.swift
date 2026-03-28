import Foundation
import SwiftUI

struct ProgressWaveConfiguration {
    let width: CGFloat
    let amplitude: CGFloat
}

let stripHeight: CGFloat = 124
let stripBorderWidth: CGFloat = 1
let transportHitSide: CGFloat = 44
let transportIconSize: CGFloat = 18
let progressWaveWidth: CGFloat = 24
let progressWaveAmplitude: CGFloat = 8
let progressWavePrimaryCycles: CGFloat = 2.4
let progressWaveHarmonicCycles: CGFloat = 4.8
let progressWaveHarmonicStrength: CGFloat = 0.32
let progressWaveConfiguration = ProgressWaveConfiguration(
    width: progressWaveWidth,
    amplitude: progressWaveAmplitude
)

public struct NowPlayingStripState: Equatable, Sendable {
    public var title: String
    public var subtitle: String
    public var isPlaying: Bool
    public var currentPositionSeconds: TimeInterval
    public var durationSeconds: TimeInterval
    public var progressFraction: Double
    public var isSeekInProgress: Bool
    public var displayedProgressFraction: Double
    public var controlsEnabled: Bool

    public init(
        title: String = "",
        subtitle: String = "",
        isPlaying: Bool = false,
        currentPositionSeconds: TimeInterval = 0,
        durationSeconds: TimeInterval = 0,
        progressFraction: Double = 0,
        isSeekInProgress: Bool = false,
        displayedProgressFraction: Double = 0,
        controlsEnabled: Bool = true
    ) {
        self.title = title
        self.subtitle = subtitle
        self.isPlaying = isPlaying
        self.currentPositionSeconds = currentPositionSeconds
        self.durationSeconds = durationSeconds
        self.progressFraction = progressFraction
        self.isSeekInProgress = isSeekInProgress
        self.displayedProgressFraction = displayedProgressFraction
        self.controlsEnabled = controlsEnabled
    }
}

public enum NowPlayingStripAction: Equatable, Sendable {
    case previousTapped
    case playPauseTapped
    case nextTapped
    case seekStarted
    case seekChanged(Double)
    case seekFinished(Double)
}

public struct NowPlayingStrip: View {
    @Environment(\.multiplayerTheme) private var theme

    private let state: NowPlayingStripState
    private let onAction: (NowPlayingStripAction) -> Void
    private let showBorder: Bool

    public init(
        state: NowPlayingStripState,
        onAction: @escaping (NowPlayingStripAction) -> Void,
        showBorder: Bool = true
    ) {
        self.state = state
        self.onAction = onAction
        self.showBorder = showBorder
    }

    public var body: some View {
        TimelineView(.animation) { context in
            stripContent(phase: wavePhase(for: context.date))
        }
        .frame(maxWidth: .infinity, minHeight: stripHeight)
    }

    private func stripContent(phase: CGFloat) -> some View {
        VStack(spacing: theme.spacing.sm) {
            HStack(spacing: theme.spacing.xs) {
                transportButton(
                    icon: theme.icons.previous,
                    accessibilityLabel: "Предыдущий трек",
                    action: .previousTapped
                )

                playPauseButton

                transportButton(
                    icon: theme.icons.next,
                    accessibilityLabel: "Следующий трек",
                    action: .nextTapped
                )
            }

            HStack(spacing: theme.spacing.sm) {
                timeLabel(formattedTime(state.currentPositionSeconds))
                Spacer(minLength: 0)
                timeLabel(formattedTime(state.durationSeconds))
            }
        }
        .padding(.horizontal, theme.spacing.sm)
        .padding(.vertical, theme.spacing.md)
        .frame(maxWidth: .infinity, minHeight: stripHeight, alignment: .center)
        .background {
            NowPlayingStripBackground(
                progress: state.displayedProgressFraction,
                isWaveAnimated: state.isPlaying && !state.isSeekInProgress,
                phase: phase,
                cornerRadius: theme.radius.large,
                showBorder: showBorder
            )
        }
        .opacity(state.controlsEnabled ? 1 : 0.72)
    }

    private var playPauseButton: some View {
        Button {
            onAction(.playPauseTapped)
        } label: {
            HStack(spacing: theme.spacing.sm) {
                playPauseIcon

                VStack(alignment: .center, spacing: theme.spacing.xxs) {
                    MultiplayerText(
                        verbatim: state.title,
                        style: theme.typography.title,
                        color: theme.colors.miniPlayerPrimaryContent,
                        alignment: .center,
                        lineLimit: 1
                    )

                    if !state.subtitle.isEmpty {
                        MultiplayerText(
                            verbatim: state.subtitle,
                            style: theme.typography.secondaryBody,
                            color: theme.colors.miniPlayerSecondaryContent,
                            alignment: .center,
                            lineLimit: 1
                        )
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.horizontal, theme.spacing.xs)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!state.controlsEnabled)
    }

    @ViewBuilder
    private func transportButton(
        icon: Image,
        accessibilityLabel: String,
        action: NowPlayingStripAction
    ) -> some View {
        Button {
            onAction(action)
        } label: {
            icon
                .resizable()
                .scaledToFit()
                .frame(width: transportIconSize, height: transportIconSize)
                .foregroundStyle(theme.colors.miniPlayerPrimaryContent)
                .frame(width: transportHitSide, height: transportHitSide)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!state.controlsEnabled)
        .accessibilityLabel(Text(accessibilityLabel))
    }

    private var playPauseIcon: some View {
        let icon = state.isPlaying ? theme.icons.pause : theme.icons.play
        let label = state.isPlaying ? "Пауза" : "Воспроизвести"

        return icon
            .resizable()
            .scaledToFit()
            .frame(width: transportIconSize, height: transportIconSize)
            .foregroundStyle(theme.colors.miniPlayerPrimaryContent)
            .accessibilityLabel(Text(label))
    }

    @ViewBuilder
    private func timeLabel(_ text: String) -> some View {
        MultiplayerText(
            verbatim: text,
            style: theme.typography.meta,
            color: theme.colors.miniPlayerSecondaryContent
        )
        .monospacedDigit()
        .frame(minWidth: 38, alignment: .center)
    }

    private func wavePhase(for date: Date) -> CGFloat {
        guard state.isPlaying, !state.isSeekInProgress else {
            return 0
        }

        let cycleDuration = 2.0
        let normalized = date.timeIntervalSinceReferenceDate
            .truncatingRemainder(dividingBy: cycleDuration) / cycleDuration
        return CGFloat(normalized) * .pi * 2
    }
}
