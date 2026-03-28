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
    public let surface2GradientStart: Color
    public let surface2GradientEnd: Color
    public let surface3GradientStart: Color
    public let surface3GradientEnd: Color
    public let surface4GradientStart: Color
    public let surface4GradientEnd: Color
    public let textPrimary: Color
    public let textSecondary: Color
    public let textTertiary: Color
    public let textInverse: Color
    public let accent: Color
    public let accentMuted: Color
    public let brandVisualPrimary: Color
    public let brandVisualPrimaryMuted: Color
    public let brandVisualPrimaryContainer: Color
    public let brandVisualSecondary: Color
    public let brandVisualSecondaryMuted: Color
    public let brandVisualTertiary: Color
    public let brandVisualTertiaryMuted: Color
    public let ctaGradientStart: Color
    public let ctaGradientEnd: Color
    public let miniPlayerGradientStart: Color
    public let miniPlayerGradientEnd: Color
    public let miniPlayerProgress: Color
    public let miniPlayerPrimaryContent: Color
    public let miniPlayerSecondaryContent: Color
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

    public var surface2Gradient: LinearGradient {
        LinearGradient(
            colors: [surface2GradientStart, surface2GradientEnd],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    public var surface3Gradient: LinearGradient {
        LinearGradient(
            colors: [surface3GradientStart, surface3GradientEnd],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    public var surface4Gradient: LinearGradient {
        LinearGradient(
            colors: [surface4GradientStart, surface4GradientEnd],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}
