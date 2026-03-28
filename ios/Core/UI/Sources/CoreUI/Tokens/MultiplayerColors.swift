import SwiftUI

public struct MultiplayerColorRamp {
    public let start: Color
    public let end: Color

    public init(start: Color, end: Color) {
        self.start = start
        self.end = end
    }

    public var colors: [Color] {
        [start, end]
    }

    public func linearGradient(
        startPoint: UnitPoint = .topLeading,
        endPoint: UnitPoint = .bottomTrailing
    ) -> LinearGradient {
        LinearGradient(
            colors: colors,
            startPoint: startPoint,
            endPoint: endPoint
        )
    }
}

public struct MultiplayerColors {
    public let background: Color
    public let backgroundGradient: MultiplayerColorRamp
    public let surfacePrimary: Color
    public let surfaceOverlay: Color
    public let surface2Gradient: MultiplayerColorRamp
    public let surface3Gradient: MultiplayerColorRamp
    public let surface4Gradient: MultiplayerColorRamp
    public let textPrimary: Color
    public let textSecondary: Color
    public let surfaceContentPrimary: Color
    public let surfaceContentSecondary: Color
    public let textInverse: Color
    public let accent: Color
    public let brandVisualPrimary: Color
    public let brandVisualPrimaryMuted: Color
    public let brandVisualPrimaryContainer: Color
    public let brandVisualSecondary: Color
    public let brandVisualTertiary: Color
    public let ctaGradient: MultiplayerColorRamp
    public let miniPlayerGradient: MultiplayerColorRamp
    public let miniPlayerProgress: Color
    public let miniPlayerPrimaryContent: Color
    public let miniPlayerSecondaryContent: Color
    public let borderSubtle: Color
}
