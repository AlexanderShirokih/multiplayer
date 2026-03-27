import SwiftUI

public struct MultiplayerTheme {
    public let colors: MultiplayerColors
    public let spacing: MultiplayerSpacing
    public let radius: MultiplayerRadius
    public let elevation: MultiplayerElevation
    public let icons: MultiplayerIcons
    public let typography: MultiplayerTypography

    public init(
        colors: MultiplayerColors,
        spacing: MultiplayerSpacing = MultiplayerSpacing(),
        radius: MultiplayerRadius = MultiplayerRadius(),
        elevation: MultiplayerElevation = MultiplayerElevation(),
        icons: MultiplayerIcons = MultiplayerIcons(),
        typography: MultiplayerTypography = defaultMultiplayerTypography()
    ) {
        self.colors = colors
        self.spacing = spacing
        self.radius = radius
        self.elevation = elevation
        self.icons = icons
        self.typography = typography
    }

    public static func `default`(for colorScheme: ColorScheme) -> MultiplayerTheme {
        MultiplayerTheme(
            colors: multiplayerColors(for: colorScheme)
        )
    }
}

private struct MultiplayerThemeKey: EnvironmentKey {
    static var defaultValue: MultiplayerTheme {
        MultiplayerTheme.default(for: .light)
    }
}

public extension EnvironmentValues {
    var multiplayerTheme: MultiplayerTheme {
        get { self[MultiplayerThemeKey.self] }
        set { self[MultiplayerThemeKey.self] = newValue }
    }
}

internal func multiplayerColors(for colorScheme: ColorScheme) -> MultiplayerColors {
    switch colorScheme {
    case .dark:
        darkMultiplayerColors()
    default:
        lightMultiplayerColors()
    }
}

private func lightMultiplayerColors() -> MultiplayerColors {
    MultiplayerColors(
        background: gray100,
        backgroundMuted: gray200,
        backgroundGradientStart: gray100,
        backgroundGradientEnd: gray200,
        surfacePrimary: .white,
        surfaceSecondary: gray200,
        surfaceOverlay: gray200.opacity(0.92),
        surfaceAccent: blue300,
        textPrimary: ink950,
        textSecondary: gray700,
        textInverse: .white,
        accent: blue500,
        accentMuted: blue300,
        brandVisualPrimary: blue500,
        brandVisualPrimaryMuted: blue300,
        brandVisualPrimaryContainer: blue400,
        brandVisualSecondary: amber300,
        brandVisualSecondaryMuted: amber300.opacity(0.48),
        ctaGradientStart: amber300,
        ctaGradientEnd: amberGradientLight,
        borderSubtle: gray300,
        borderStrong: gray500,
        success: blue400,
        warning: amber300,
        error: red300
    )
}

private func darkMultiplayerColors() -> MultiplayerColors {
    MultiplayerColors(
        background: ink950,
        backgroundMuted: gray900,
        backgroundGradientStart: ink950,
        backgroundGradientEnd: gray900,
        surfacePrimary: gray800,
        surfaceSecondary: gray700,
        surfaceOverlay: gray800.opacity(0.88),
        surfaceAccent: blue500,
        textPrimary: gray100,
        textSecondary: gray300,
        textInverse: ink950,
        accent: blue300,
        accentMuted: blue400,
        brandVisualPrimary: blue400,
        brandVisualPrimaryMuted: blue300,
        brandVisualPrimaryContainer: blue500,
        brandVisualSecondary: amber300,
        brandVisualSecondaryMuted: amber300.opacity(0.48),
        ctaGradientStart: amberGradientLight,
        ctaGradientEnd: amber300,
        borderSubtle: gray600,
        borderStrong: gray500,
        success: blue300,
        warning: amber300,
        error: red300
    )
}
