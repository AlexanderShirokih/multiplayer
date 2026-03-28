import SwiftUI

private let vinylRecordSizeFraction: CGFloat = 0.768
private let vinylLabelSizeFraction: CGFloat = 0.26
private let vinylSpindleSizeFraction: CGFloat = 0.058
private let vinylGrooveLineWidthFraction: CGFloat = 0.008
private let vinylHighlightWidthFraction: CGFloat = 0.2
private let vinylHighlightHeightFraction: CGFloat = 0.68
private let vinylHighlightOffsetXFraction: CGFloat = 0.14
private let vinylHighlightOffsetYFraction: CGFloat = -0.08

public struct MultiplayerVinylCover: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.multiplayerTheme) private var theme

    private let contentDescription: String?

    public init(contentDescription: String? = nil) {
        self.contentDescription = contentDescription
    }

    public var body: some View {
        GeometryReader { proxy in
            let side = min(proxy.size.width, proxy.size.height)
            let recordSize = side * vinylRecordSizeFraction
            let grooveLineWidth = side * vinylGrooveLineWidthFraction
            let labelSize = recordSize * vinylLabelSizeFraction
            let spindleSize = recordSize * vinylSpindleSizeFraction
            let highlightWidth = recordSize * vinylHighlightWidthFraction
            let highlightHeight = recordSize * vinylHighlightHeightFraction

            ZStack {
                RoundedRectangle(cornerRadius: theme.radius.large, style: .continuous)
                    .fill(backgroundGradient)

                RoundedRectangle(cornerRadius: theme.radius.large, style: .continuous)
                    .stroke(borderGradient, lineWidth: 1)

                ZStack {
                    Circle()
                        .fill(
                            RadialGradient(
                                colors: [
                                    Color.white.opacity(0.12),
                                    Color(hex: 0x212736),
                                    Color.black.opacity(0.98)
                                ],
                                center: .center,
                                startRadius: 0,
                                endRadius: recordSize * 0.5
                            )
                        )

                    ForEach(0..<6, id: \.self) { index in
                        Circle()
                            .stroke(
                                Color.white.opacity(index == 0 ? 0.11 : 0.06),
                                lineWidth: grooveLineWidth
                            )
                            .padding(recordSize * (0.09 + CGFloat(index) * 0.07))
                    }

                    Ellipse()
                        .fill(
                            LinearGradient(
                                colors: [
                                    Color.white.opacity(0.24),
                                    Color.white.opacity(0.02)
                                ],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(width: highlightWidth, height: highlightHeight)
                        .rotationEffect(.degrees(-18))
                        .offset(
                            x: recordSize * vinylHighlightOffsetXFraction,
                            y: recordSize * vinylHighlightOffsetYFraction
                        )

                    Circle()
                        .fill(
                            LinearGradient(
                                colors: [
                                    theme.colors.brandVisualPrimary.opacity(0.92),
                                    theme.colors.brandVisualTertiary.opacity(0.86)
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .frame(width: labelSize, height: labelSize)

                    Circle()
                        .fill(Color.white.opacity(0.92))
                        .frame(width: spindleSize, height: spindleSize)
                }
                .frame(width: recordSize, height: recordSize)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(contentDescription ?? "Виниловая обложка"))
    }

    private var backgroundGradient: LinearGradient {
        if colorScheme == .dark {
            return LinearGradient(
            colors: [
                Color(hex: 0x0E1628),
                Color(hex: 0x17233B)
            ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }

        return LinearGradient(
            colors: [
                Color(hex: 0xEFF5FF),
                Color(hex: 0xC8D8FC)
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var borderGradient: LinearGradient {
        LinearGradient(
            colors: [
                Color.white.opacity(0.26),
                Color(hex: 0xD9E6FF, opacity: 0.04)
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

#Preview("Vinyl Light") {
    MultiplayerPreview(colorScheme: .light, alignment: .center) {
        MultiplayerVinylCover()
            .frame(width: 184, height: 184)
    }
}

#Preview("Vinyl Dark") {
    MultiplayerPreview(colorScheme: .dark, alignment: .center) {
        MultiplayerVinylCover()
            .frame(width: 184, height: 184)
    }
}

private extension Color {
    init(hex: UInt, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}
