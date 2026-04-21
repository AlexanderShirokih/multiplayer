import SwiftUI

private let equalizerBarWidth: CGFloat = 6
private let equalizerBarGap: CGFloat = 2
private let equalizerBarMaxHeight: CGFloat = 26
private let equalizerBarCornerRadius: CGFloat = 3
private let equalizerRestingHeights: [CGFloat] = [0.3, 0.2, 0.4]

public struct MultiplayerEqualizerIndicator: View {
    private let color: Color
    private let isAnimated: Bool

    public init(
        color: Color,
        isAnimated: Bool = true
    ) {
        self.color = color
        self.isAnimated = isAnimated
    }

    public var body: some View {
        TimelineView(.animation(minimumInterval: equalizerFrameInterval, paused: !isAnimated)) { context in
            let heights = barHeights(at: context.date)
            HStack(alignment: .center, spacing: equalizerBarGap) {
                equalizerBar(heightFraction: heights[0])
                equalizerBar(heightFraction: heights[1])
                equalizerBar(heightFraction: heights[2])
            }
        }
        .frame(height: equalizerBarMaxHeight, alignment: .center)
    }

    private func equalizerBar(heightFraction: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: equalizerBarCornerRadius, style: .continuous)
            .fill(color)
            .frame(width: equalizerBarWidth, height: equalizerBarMaxHeight * heightFraction)
    }

    private func barHeights(at date: Date) -> [CGFloat] {
        guard isAnimated else {
            return equalizerRestingHeights
        }
        let phase = date.timeIntervalSinceReferenceDate
        return equalizerAnimationPhases.map { animation in
            animation.height(at: phase)
        }
    }
}

private struct EqualizerAnimationPhase {
    let speed: Double
    let offset: Double
    let minimumHeight: CGFloat
    let maximumHeight: CGFloat

    func height(at phase: Double) -> CGFloat {
        let normalized = (sin((phase * speed) + offset) + 1) / 2
        return minimumHeight + (maximumHeight - minimumHeight) * normalized
    }
}

private let equalizerAnimationPhases: [EqualizerAnimationPhase] = [
    EqualizerAnimationPhase(speed: 8.0, offset: 0.0, minimumHeight: 0.25, maximumHeight: 0.85),
    EqualizerAnimationPhase(speed: 10.0, offset: 1.3, minimumHeight: 0.2, maximumHeight: 1.0),
    EqualizerAnimationPhase(speed: 7.0, offset: 2.1, minimumHeight: 0.3, maximumHeight: 0.75)
]
private let equalizerFrameInterval = 1.0 / 15.0
