import SwiftUI

public enum MultiplayerCardSurfaceStyle: Sendable {
    case surface1
    case surface2
    case surface3
    case surface4
}

public struct MultiplayerCardSurface<Content: View>: View {
    @Environment(\.multiplayerTheme) private var theme

    private let style: MultiplayerCardSurfaceStyle
    private let cornerRadius: CGFloat?
    private let contentPadding: EdgeInsets
    private let content: Content

    public init(
        style: MultiplayerCardSurfaceStyle,
        cornerRadius: CGFloat? = nil,
        contentPadding: EdgeInsets = EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0),
        @ViewBuilder content: () -> Content
    ) {
        self.style = style
        self.cornerRadius = cornerRadius
        self.contentPadding = contentPadding
        self.content = content()
    }

    public var body: some View {
        let resolvedCornerRadius = cornerRadius ?? theme.radius.xLarge

        content
            .padding(contentPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background {
                RoundedRectangle(cornerRadius: resolvedCornerRadius, style: .continuous)
                    .fill(fillStyle)
            }
            .overlay {
                RoundedRectangle(cornerRadius: resolvedCornerRadius, style: .continuous)
                    .stroke(borderColor, lineWidth: 1)
            }
            .clipShape(
                RoundedRectangle(cornerRadius: resolvedCornerRadius, style: .continuous)
            )
            .shadow(
                color: shadowColor,
                radius: theme.elevation.level2.radius,
                x: theme.elevation.level2.offsetX,
                y: theme.elevation.level2.offsetY
            )
    }

    private var fillStyle: AnyShapeStyle {
        switch style {
        case .surface1:
            AnyShapeStyle(theme.colors.surfacePrimary)

        case .surface2:
            AnyShapeStyle(theme.colors.surface2Gradient.linearGradient())

        case .surface3:
            AnyShapeStyle(theme.colors.surface3Gradient.linearGradient())

        case .surface4:
            AnyShapeStyle(theme.colors.surface4Gradient.linearGradient())
        }
    }

    private var borderColor: Color {
        switch style {
        case .surface1:
            theme.colors.borderSubtle.opacity(0.65)

        case .surface2, .surface3, .surface4:
            theme.colors.borderSubtle.opacity(0.42)
        }
    }

    private var shadowColor: Color {
        theme.elevation.level2.color.opacity(0.7)
    }
}
