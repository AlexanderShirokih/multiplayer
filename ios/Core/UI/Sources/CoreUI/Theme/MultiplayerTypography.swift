import CoreGraphics
import SwiftUI

public struct MultiplayerTextStyle {
    public let font: Font
    public let lineSpacing: CGFloat
    public let tracking: CGFloat

    public init(
        font: Font,
        lineSpacing: CGFloat = 0,
        tracking: CGFloat = 0
    ) {
        self.font = font
        self.lineSpacing = lineSpacing
        self.tracking = tracking
    }
}

public struct MultiplayerTypography {
    public let displayLarge: MultiplayerTextStyle
    public let headlineLarge: MultiplayerTextStyle
    public let titleLarge: MultiplayerTextStyle
    public let titleMedium: MultiplayerTextStyle
    public let bodyLarge: MultiplayerTextStyle
    public let bodyMedium: MultiplayerTextStyle
    public let labelLarge: MultiplayerTextStyle

    public init(
        displayLarge: MultiplayerTextStyle,
        headlineLarge: MultiplayerTextStyle,
        titleLarge: MultiplayerTextStyle,
        titleMedium: MultiplayerTextStyle,
        bodyLarge: MultiplayerTextStyle,
        bodyMedium: MultiplayerTextStyle,
        labelLarge: MultiplayerTextStyle
    ) {
        self.displayLarge = displayLarge
        self.headlineLarge = headlineLarge
        self.titleLarge = titleLarge
        self.titleMedium = titleMedium
        self.bodyLarge = bodyLarge
        self.bodyMedium = bodyMedium
        self.labelLarge = labelLarge
    }
}

public func defaultMultiplayerTypography() -> MultiplayerTypography {
    MultiplayerTypography(
        displayLarge: MultiplayerTextStyle(
            font: .system(.largeTitle, design: .rounded, weight: .bold),
            lineSpacing: 4
        ),
        headlineLarge: MultiplayerTextStyle(
            font: .system(.title2, design: .rounded, weight: .semibold),
            lineSpacing: 2
        ),
        titleLarge: MultiplayerTextStyle(
            font: .system(.title3, design: .rounded, weight: .semibold),
            lineSpacing: 2
        ),
        titleMedium: MultiplayerTextStyle(
            font: .system(.headline, design: .default, weight: .semibold)
        ),
        bodyLarge: MultiplayerTextStyle(
            font: .system(.body, design: .default, weight: .regular),
            lineSpacing: 4
        ),
        bodyMedium: MultiplayerTextStyle(
            font: .system(.callout, design: .default, weight: .regular),
            lineSpacing: 2
        ),
        labelLarge: MultiplayerTextStyle(
            font: .system(.subheadline, design: .default, weight: .semibold),
            tracking: 0.2
        )
    )
}

public extension View {
    func multiplayerTextStyle(_ style: MultiplayerTextStyle) -> some View {
        font(style.font)
            .tracking(style.tracking)
            .lineSpacing(style.lineSpacing)
    }
}
