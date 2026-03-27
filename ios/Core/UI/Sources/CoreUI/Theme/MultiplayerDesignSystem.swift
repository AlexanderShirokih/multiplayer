import SwiftUI

public struct MultiplayerDesignSystem<Content: View>: View {
    @Environment(\.colorScheme) private var systemColorScheme

    private let colorSchemeOverride: ColorScheme?
    private let content: Content

    public init(
        colorScheme: ColorScheme? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.colorSchemeOverride = colorScheme
        self.content = content()
    }

    public var body: some View {
        let effectiveColorScheme = colorSchemeOverride ?? systemColorScheme

        content
            .environment(\.multiplayerTheme, .default(for: effectiveColorScheme))
            .preferredColorScheme(colorSchemeOverride)
    }
}

public extension View {
    func multiplayerDesignSystem(colorScheme: ColorScheme? = nil) -> some View {
        MultiplayerDesignSystem(colorScheme: colorScheme) {
            self
        }
    }
}
