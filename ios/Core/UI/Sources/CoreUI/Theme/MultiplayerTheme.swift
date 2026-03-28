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

    public static func `default`(for colorScheme: ColorScheme) -> Self {
        Self(
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
        backgroundGradient: MultiplayerColorRamp(start: gray100, end: blueGray50),
        surfacePrimary: .white,
        surfaceOverlay: gray200.opacity(0.92),
        surface2Gradient: MultiplayerColorRamp(start: .white, end: blueGray50),
        surface3Gradient: MultiplayerColorRamp(start: lavender50, end: lavender100),
        surface4Gradient: MultiplayerColorRamp(start: peach50, end: neutralBlue50),
        textPrimary: ink950,
        textSecondary: gray700,
        surfaceContentPrimary: gray650,
        surfaceContentSecondary: gray500,
        textInverse: .white,
        accent: blue500,
        brandVisualPrimary: blue500,
        brandVisualPrimaryMuted: blue300,
        brandVisualPrimaryContainer: blue400,
        brandVisualSecondary: amber300,
        brandVisualTertiary: purple400,
        ctaGradient: MultiplayerColorRamp(start: amber300, end: amberGradientLight),
        miniPlayerGradient: MultiplayerColorRamp(start: miniPlayerLightStart, end: miniPlayerLightEnd),
        miniPlayerProgress: amber300,
        miniPlayerPrimaryContent: gray650,
        miniPlayerSecondaryContent: miniPlayerLightSecondary,
        borderSubtle: gray300
    )
}

private func darkMultiplayerColors() -> MultiplayerColors {
    MultiplayerColors(
        background: ink950,
        backgroundGradient: MultiplayerColorRamp(start: ink950, end: navy600),
        surfacePrimary: gray900,
        surfaceOverlay: gray900.opacity(0.88),
        surface2Gradient: MultiplayerColorRamp(start: gray900, end: gray750),
        surface3Gradient: MultiplayerColorRamp(start: indigo900, end: indigo700),
        surface4Gradient: MultiplayerColorRamp(start: slate900, end: slate600),
        textPrimary: gray100,
        textSecondary: gray300,
        surfaceContentPrimary: gray100,
        surfaceContentSecondary: gray400,
        textInverse: ink950,
        accent: blue300,
        brandVisualPrimary: blue400,
        brandVisualPrimaryMuted: blue300,
        brandVisualPrimaryContainer: blue500,
        brandVisualSecondary: amber300,
        brandVisualTertiary: purple400,
        ctaGradient: MultiplayerColorRamp(start: amberGradientLight, end: amber300),
        miniPlayerGradient: MultiplayerColorRamp(start: miniPlayerDarkStart, end: miniPlayerDarkEnd),
        miniPlayerProgress: amber300,
        miniPlayerPrimaryContent: gray100,
        miniPlayerSecondaryContent: miniPlayerDarkSecondary,
        borderSubtle: gray600
    )
}
