import SwiftUI

public struct MultiplayerPreview<Content: View>: View {
    private let colorScheme: ColorScheme
    private let alignment: Alignment
    private let content: Content

    public init(
        colorScheme: ColorScheme = .dark,
        alignment: Alignment = .topLeading,
        @ViewBuilder content: () -> Content
    ) {
        self.colorScheme = colorScheme
        self.alignment = alignment
        self.content = content()
    }

    public var body: some View {
        MultiplayerDesignSystem(colorScheme: colorScheme) {
            MultiplayerPreviewContainer(
                alignment: alignment,
                content: { content }
            )
        }
    }
}

private struct MultiplayerPreviewContainer<Content: View>: View {
    @Environment(\.multiplayerTheme) private var theme

    let alignment: Alignment
    let content: () -> Content

    var body: some View {
        ZStack(alignment: alignment) {
            MultiplayerBrandBackground()

            content()
                .padding(theme.spacing.lg)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment)
        }
    }
}

#Preview("Multiplayer Preview") {
    MultiplayerPreview(alignment: .center) {
        PreviewCard()
    }
}

private struct PreviewCard: View {
    @Environment(\.multiplayerTheme) private var theme

    var body: some View {
        MultiplayerSurface(
            cornerRadius: theme.radius.large,
            color: theme.colors.surfacePrimary,
            elevation: theme.elevation.level2
        ) {
            MultiplayerText(
                verbatim: "Now Playing",
                style: theme.typography.title,
                color: theme.colors.textPrimary
            )
            .padding(theme.spacing.lg)
        }
    }
}
