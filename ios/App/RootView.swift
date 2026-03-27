import CoreUI
import SwiftUI

struct RootView: View {
    @Environment(\.multiplayerTheme) private var theme

    var body: some View {
        ZStack {
            MultiplayerBrandBackground()

            VStack(spacing: theme.spacing.lg) {
                Image("AppIconPreview", bundle: nil)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 112, height: 112)
                    .clipShape(
                        RoundedRectangle(
                            cornerRadius: theme.radius.large,
                            style: .continuous
                        )
                    )
                    .shadow(
                        color: theme.elevation.level3.color,
                        radius: theme.elevation.level3.radius,
                        x: theme.elevation.level3.x,
                        y: theme.elevation.level3.y
                    )

                VStack(spacing: theme.spacing.sm) {
                    MultiplayerText(
                        verbatim: "MultiPlayer",
                        style: theme.typography.displayLarge,
                        color: theme.colors.textPrimary,
                        alignment: .center
                    )

                    MultiplayerText(
                        verbatim: "iOS foundation is ready. Feature modules can now grow from a shared design language instead of ad-hoc styling.",
                        style: theme.typography.bodyLarge,
                        color: theme.colors.textSecondary,
                        alignment: .center
                    )
                    .frame(maxWidth: 320)
                }

                MultiplayerSurface(
                    cornerRadius: theme.radius.xLarge,
                    color: theme.colors.surfaceOverlay,
                    contentColor: theme.colors.textPrimary,
                    elevation: theme.elevation.level2,
                    border: MultiplayerBorderStyle(
                        color: theme.colors.borderSubtle.opacity(0.6)
                    )
                ) {
                    HStack(spacing: theme.spacing.sm) {
                        theme.icons.play
                            .font(.headline)
                            .foregroundStyle(theme.colors.textInverse)
                            .frame(width: 40, height: 40)
                            .background(theme.colors.accent)
                            .clipShape(Circle())

                        VStack(alignment: .leading, spacing: theme.spacing.xxs) {
                            MultiplayerText(
                                verbatim: "CoreUI is connected",
                                style: theme.typography.titleMedium,
                                color: theme.colors.textPrimary
                            )

                            MultiplayerText(
                                verbatim: "Colors, spacing, typography and surfaces now come from one module.",
                                style: theme.typography.bodyMedium,
                                color: theme.colors.textSecondary
                            )
                        }

                        Spacer(minLength: theme.spacing.sm)
                    }
                    .padding(.horizontal, theme.spacing.md)
                    .padding(.vertical, theme.spacing.sm)
                }
            }
            .padding(theme.spacing.xl)
        }
    }
}

#Preview {
    MultiplayerDesignSystem {
        RootView()
    }
}

#Preview("Light") {
    MultiplayerDesignSystem(colorScheme: .light) {
        RootView()
    }
}
