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
    public let pageTitle: MultiplayerTextStyle
    public let title: MultiplayerTextStyle
    public let compactTitle: MultiplayerTextStyle
    public let body: MultiplayerTextStyle
    public let secondaryBody: MultiplayerTextStyle
    public let meta: MultiplayerTextStyle
    public let label: MultiplayerTextStyle

    public init(
        pageTitle: MultiplayerTextStyle,
        title: MultiplayerTextStyle,
        compactTitle: MultiplayerTextStyle,
        body: MultiplayerTextStyle,
        secondaryBody: MultiplayerTextStyle,
        meta: MultiplayerTextStyle,
        label: MultiplayerTextStyle
    ) {
        self.pageTitle = pageTitle
        self.title = title
        self.compactTitle = compactTitle
        self.body = body
        self.secondaryBody = secondaryBody
        self.meta = meta
        self.label = label
    }
}

public func defaultMultiplayerTypography() -> MultiplayerTypography {
    MultiplayerTypography(
        pageTitle: MultiplayerTextStyle(
            font: .system(size: 34, weight: .bold, design: .rounded),
            lineSpacing: 4,
            tracking: -0.6
        ),
        title: MultiplayerTextStyle(
            font: .system(size: 20, weight: .semibold, design: .rounded),
            lineSpacing: 2,
            tracking: -0.25
        ),
        compactTitle: MultiplayerTextStyle(
            font: .system(size: 18, weight: .semibold, design: .rounded),
            lineSpacing: 2,
            tracking: -0.2
        ),
        body: MultiplayerTextStyle(
            font: .system(.body, design: .default, weight: .regular),
            lineSpacing: 4
        ),
        secondaryBody: MultiplayerTextStyle(
            font: .system(.callout, design: .default, weight: .regular),
            lineSpacing: 2
        ),
        meta: MultiplayerTextStyle(
            font: .system(size: 12, weight: .regular, design: .default),
            lineSpacing: 2
        ),
        label: MultiplayerTextStyle(
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
