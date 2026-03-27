import SwiftUI

public struct MultiplayerColors {
    public let background: Color
    public let backgroundMuted: Color
    public let backgroundGradientStart: Color
    public let backgroundGradientEnd: Color
    public let surfacePrimary: Color
    public let surfaceSecondary: Color
    public let surfaceOverlay: Color
    public let surfaceAccent: Color
    public let textPrimary: Color
    public let textSecondary: Color
    public let textInverse: Color
    public let accent: Color
    public let accentMuted: Color
    public let brandVisualPrimary: Color
    public let brandVisualPrimaryMuted: Color
    public let brandVisualPrimaryContainer: Color
    public let brandVisualSecondary: Color
    public let brandVisualSecondaryMuted: Color
    public let ctaGradientStart: Color
    public let ctaGradientEnd: Color
    public let borderSubtle: Color
    public let borderStrong: Color
    public let success: Color
    public let warning: Color
    public let error: Color

    public var backgroundGradient: LinearGradient {
        LinearGradient(
            colors: [backgroundGradientStart, backgroundGradientEnd],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}
