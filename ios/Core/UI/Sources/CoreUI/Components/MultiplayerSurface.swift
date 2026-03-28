import SwiftUI

public struct MultiplayerBorderStyle {
    public let color: Color
    public let width: CGFloat

    public init(
        color: Color,
        width: CGFloat = 1
    ) {
        self.color = color
        self.width = width
    }
}

public struct MultiplayerSurface<Content: View>: View {
    @Environment(\.multiplayerTheme) private var theme

    private let cornerRadius: CGFloat?
    private let color: Color?
    private let contentColor: Color?
    private let elevation: MultiplayerShadow?
    private let border: MultiplayerBorderStyle?
    private let content: Content

    public init(
        cornerRadius: CGFloat? = nil,
        color: Color? = nil,
        contentColor: Color? = nil,
        elevation: MultiplayerShadow? = nil,
        border: MultiplayerBorderStyle? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.cornerRadius = cornerRadius
        self.color = color
        self.contentColor = contentColor
        self.elevation = elevation
        self.border = border
        self.content = content()
    }

    public var body: some View {
        let resolvedCornerRadius = cornerRadius ?? theme.radius.medium
        let resolvedColor = color ?? theme.colors.surfacePrimary
        let resolvedContentColor = contentColor ?? theme.colors.textPrimary

        content
            .foregroundStyle(resolvedContentColor)
            .background(
                RoundedRectangle(cornerRadius: resolvedCornerRadius, style: .continuous)
                    .fill(resolvedColor)
            )
            .overlay {
                if let border {
                    RoundedRectangle(cornerRadius: resolvedCornerRadius, style: .continuous)
                        .stroke(border.color, lineWidth: border.width)
                }
            }
            .clipShape(
                RoundedRectangle(cornerRadius: resolvedCornerRadius, style: .continuous)
            )
            .modifier(
                MultiplayerShadowModifier(
                    shadow: elevation
                )
            )
    }
}

private struct MultiplayerShadowModifier: ViewModifier {
    let shadow: MultiplayerShadow?

    func body(content: Content) -> some View {
        if let shadow {
            content.shadow(
                color: shadow.color,
                radius: shadow.radius,
                x: shadow.offsetX,
                y: shadow.offsetY
            )
        } else {
            content
        }
    }
}
