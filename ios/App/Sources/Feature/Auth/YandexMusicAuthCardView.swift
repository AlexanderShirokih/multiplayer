import CoreUI
import SwiftUI

struct YandexMusicAuthCardView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.multiplayerTheme) private var theme

    let onConnectYandexMusic: () -> Void

    private var palette: MultiplayerColors {
        MultiplayerTheme.default(for: colorScheme).colors
    }

    private var buttonTextColor: Color {
        colorScheme == .dark ? palette.textInverse : palette.textPrimary
    }

    var body: some View {
        MultiplayerSurface(
            cornerRadius: theme.radius.xLarge,
            color: palette.surfaceOverlay,
            contentColor: palette.textPrimary,
            elevation: theme.elevation.level3,
            border: MultiplayerBorderStyle(
                color: palette.textPrimary.opacity(0.08)
            )
        ) {
            VStack(alignment: .leading, spacing: 10) {
                MultiplayerText(
                    verbatim: "Яндекс Музыка",
                    style: MultiplayerTextStyle(
                        font: .system(size: 21, weight: .semibold, design: .rounded),
                        lineSpacing: 2,
                        tracking: -0.3
                    ),
                    color: palette.textPrimary
                )

                MultiplayerText(
                    verbatim: "Вход по OAuth через Yandex ID,\nчтобы сразу перейти к своей музыке.",
                    style: MultiplayerTextStyle(
                        font: .system(size: 11, weight: .regular, design: .default),
                        lineSpacing: 3,
                        tracking: -0.05
                    ),
                    color: palette.textSecondary
                )

                Button(action: onConnectYandexMusic) {
                    MultiplayerText(
                        verbatim: "Войти через Яндекс Музыку",
                        style: theme.typography.labelLarge,
                        color: buttonTextColor,
                        alignment: .center,
                        lineLimit: 1
                    )
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, theme.spacing.lg)
                    .padding(.vertical, theme.spacing.md)
                    .background(
                        LinearGradient(
                            colors: [
                                palette.ctaGradientStart,
                                palette.ctaGradientEnd,
                            ],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .padding(.top, theme.spacing.xxs)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
    }
}

#Preview("Card Light") {
    MultiplayerDesignSystem(colorScheme: .light) {
        YandexMusicAuthCardView(onConnectYandexMusic: {})
            .padding(24)
    }
}

#Preview("Card Dark") {
    MultiplayerDesignSystem(colorScheme: .dark) {
        YandexMusicAuthCardView(onConnectYandexMusic: {})
            .padding(24)
    }
}
