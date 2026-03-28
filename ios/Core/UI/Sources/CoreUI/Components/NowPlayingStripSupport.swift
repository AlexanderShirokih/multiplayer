import Foundation
import SwiftUI

struct NowPlayingStripBackground: View {
    @Environment(\.multiplayerTheme) private var theme

    let progress: Double
    let isWaveAnimated: Bool
    let phase: CGFloat
    let cornerRadius: CGFloat
    let showBorder: Bool

    var body: some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(
                LinearGradient(
                    colors: theme.colors.miniPlayerGradient.colors,
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .overlay {
                Canvas { context, size in
                    let rect = CGRect(origin: .zero, size: size)
                    let clipPath = Path(roundedRect: rect, cornerRadius: cornerRadius)
                    context.clip(to: clipPath)

                    let fillPath = progressFillPath(
                        size: size,
                        progress: CGFloat(progress.clamped(to: 0 ... 1)),
                        isWaveAnimated: isWaveAnimated,
                        phase: phase,
                        wave: progressWaveConfiguration
                    )

                    context.fill(
                        fillPath,
                        with: .color(theme.colors.miniPlayerProgress.opacity(0.44))
                    )
                }
            }
            .overlay {
                if showBorder {
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .stroke(theme.colors.surfacePrimary.opacity(0.46), lineWidth: stripBorderWidth)
                }
            }
    }
}

func progressFillPath(
    size: CGSize,
    progress: CGFloat,
    isWaveAnimated: Bool,
    phase: CGFloat,
    wave: ProgressWaveConfiguration
) -> Path {
    let clampedProgress = progress.clamped(to: 0 ... 1)
    guard clampedProgress > 0 else {
        return Path()
    }

    let progressWidth = size.width * clampedProgress
    var path = Path()
    path.move(to: .zero)

    if isWaveAnimated, progressWidth > 1 {
        let effectiveWaveWidth = min(wave.width, progressWidth)
        let straightSectionEnd = max(progressWidth - effectiveWaveWidth, 0)
        path.addLine(to: CGPoint(x: straightSectionEnd, y: 0))

        let samples = 20
        for index in 0 ... samples {
            let normalized = CGFloat(index) / CGFloat(samples)
            let pointY = size.height * normalized
            let primary = sin((normalized * .pi * progressWavePrimaryCycles) + phase)
            let harmonic = sin(
                (normalized * .pi * progressWaveHarmonicCycles) + (phase * 1.35)
            ) * progressWaveHarmonicStrength
            let composite = ((primary + harmonic + 1.0) / (2.0 + progressWaveHarmonicStrength))
                .clamped(to: 0 ... 1)
            let edgeOffset = composite * min(wave.amplitude, progressWidth)
            let pointX = max(progressWidth - edgeOffset, 0)
            path.addLine(to: CGPoint(x: pointX, y: pointY))
        }
    } else {
        path.addLine(to: CGPoint(x: progressWidth, y: 0))
        path.addLine(to: CGPoint(x: progressWidth, y: size.height))
    }

    path.addLine(to: CGPoint(x: 0, y: size.height))
    path.closeSubpath()
    return path
}

func formattedTime(_ time: TimeInterval) -> String {
    let clampedTime = max(time, 0)
    let totalSeconds = Int(clampedTime.rounded(.down))
    let hours = totalSeconds / 3_600
    let minutes = (totalSeconds % 3_600) / 60
    let seconds = totalSeconds % 60

    if hours > 0 {
        return String(format: "%d:%02d:%02d", hours, minutes, seconds)
    }

    return String(format: "%d:%02d", minutes, seconds)
}

extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
