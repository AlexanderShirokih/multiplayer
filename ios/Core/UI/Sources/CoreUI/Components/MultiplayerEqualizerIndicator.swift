import SwiftUI

private let equalizerBarWidth: CGFloat = 6
private let equalizerBarGap: CGFloat = 2
private let equalizerBarMaxHeight: CGFloat = 26
private let equalizerBarCornerRadius: CGFloat = 3

public struct MultiplayerEqualizerIndicator: View {
    private let color: Color

    @State private var bar1Height: CGFloat = 0.28
    @State private var bar2Height: CGFloat = 0.45
    @State private var bar3Height: CGFloat = 0.32

    public init(color: Color) {
        self.color = color
    }

    public var body: some View {
        HStack(alignment: .center, spacing: equalizerBarGap) {
            equalizerBar(heightFraction: bar1Height)
            equalizerBar(heightFraction: bar2Height)
            equalizerBar(heightFraction: bar3Height)
        }
        .frame(height: equalizerBarMaxHeight, alignment: .center)
        .task { await startAnimations() }
    }

    private func equalizerBar(heightFraction: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: equalizerBarCornerRadius, style: .continuous)
            .fill(color)
            .frame(width: equalizerBarWidth, height: equalizerBarMaxHeight * heightFraction)
    }

    private func startAnimations() async {
        withAnimation(.easeInOut(duration: 0.42).repeatForever(autoreverses: true)) {
            bar1Height = 1.0
        }
        try? await Task.sleep(for: .milliseconds(210))
        withAnimation(.easeInOut(duration: 0.56).repeatForever(autoreverses: true)) {
            bar2Height = 1.0
        }
        try? await Task.sleep(for: .milliseconds(160))
        withAnimation(.easeInOut(duration: 0.48).repeatForever(autoreverses: true)) {
            bar3Height = 1.0
        }
    }
}
