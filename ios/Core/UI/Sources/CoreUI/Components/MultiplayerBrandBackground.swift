import SwiftUI

public struct MultiplayerBrandBackground: View {
    @Environment(\.multiplayerTheme) private var theme

    public init() {}

    public var body: some View {
        ZStack {
            theme.colors.backgroundGradient

            DecorativeGlow(
                colorStops: [
                    theme.colors.brandVisualPrimary.opacity(0.98),
                    theme.colors.brandVisualPrimary.opacity(0.58),
                    theme.colors.brandVisualPrimaryContainer.opacity(0.22),
                    .clear,
                ],
                size: 340,
                blurRadius: 84
            )
            .offset(x: 40, y: -18)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)

            DecorativeGlow(
                colorStops: [
                    theme.colors.brandVisualPrimaryMuted.opacity(0.9),
                    theme.colors.brandVisualPrimaryContainer.opacity(0.42),
                    .clear,
                ],
                size: 168,
                blurRadius: 28
            )
            .offset(x: 18, y: 36)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)

            DecorativeGlow(
                colorStops: [
                    theme.colors.brandVisualSecondary.opacity(0.88),
                    theme.colors.brandVisualSecondary.opacity(0.34),
                    .clear,
                ],
                size: 232,
                blurRadius: 58
            )
            .offset(x: -40, y: 158)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

            DecorativeGlow(
                colorStops: [
                    theme.colors.brandVisualSecondary.opacity(0.74),
                    theme.colors.brandVisualSecondary.opacity(0.2),
                    .clear,
                ],
                size: 96,
                blurRadius: 18
            )
            .offset(x: 4, y: 190)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

            DecorativeGlow(
                colorStops: [
                    theme.colors.brandVisualPrimaryContainer.opacity(0.34),
                    .clear,
                ],
                size: 320,
                blurRadius: 58
            )
            .offset(x: 18, y: 42)
        }
        .ignoresSafeArea()
    }
}

private struct DecorativeGlow: View {
    let colorStops: [Color]
    let size: CGFloat
    let blurRadius: CGFloat

    var body: some View {
        Circle()
            .fill(
                RadialGradient(
                    colors: colorStops,
                    center: .center,
                    startRadius: 0,
                    endRadius: size / 2
                )
            )
            .frame(width: size, height: size)
            .blur(radius: blurRadius)
    }
}
