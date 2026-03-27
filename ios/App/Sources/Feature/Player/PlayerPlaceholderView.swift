import CoreUI
import SwiftUI

struct PlayerPlaceholderView: View {
    var body: some View {
        PlayerPlaceholderContentView()
    }
}

private struct PlayerPlaceholderContentView: View {
    @Environment(\.multiplayerTheme) private var theme

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    theme.colors.backgroundGradientStart,
                    theme.colors.backgroundGradientEnd,
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            MultiplayerBrandBackground()

            VStack {
                Spacer(minLength: 0)

                MultiplayerSurface(
                    cornerRadius: theme.radius.xLarge,
                    elevation: theme.elevation.level2
                ) {
                    VStack(spacing: theme.spacing.sm) {
                        MultiplayerText(
                            verbatim: "Экран плеера",
                            style: theme.typography.titleLarge,
                            color: theme.colors.textPrimary,
                            alignment: .center
                        )

                        MultiplayerText(
                            verbatim: "Пока здесь пусто. Следующим шагом подключим now playing и базовые контролы воспроизведения.",
                            style: theme.typography.bodyMedium,
                            color: theme.colors.textSecondary,
                            alignment: .center
                        )
                    }
                    .padding(.horizontal, theme.spacing.xl)
                    .padding(.vertical, theme.spacing.xxxl)
                    .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, theme.spacing.lg)

                Spacer(minLength: 0)
            }
            .safeAreaPadding()
        }
    }
}

#Preview("Light") {
    MultiplayerDesignSystem(colorScheme: .light) {
        PlayerPlaceholderView()
    }
}

#Preview("Dark") {
    MultiplayerDesignSystem(colorScheme: .dark) {
        PlayerPlaceholderView()
    }
}
