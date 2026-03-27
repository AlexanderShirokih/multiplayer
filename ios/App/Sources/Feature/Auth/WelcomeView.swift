import AuthFeature
import CoreUI
import SwiftUI

struct WelcomeView: View {
    private let viewModel: YandexMusicAuthCardViewModel

    init(viewModel: YandexMusicAuthCardViewModel) {
        self.viewModel = viewModel
    }

    var body: some View {
        WelcomeContentView(
            viewModel: viewModel
        )
    }
}

private struct WelcomeContentView: View {
    @Environment(\.multiplayerTheme) private var theme

    let viewModel: YandexMusicAuthCardViewModel

    var body: some View {
        GeometryReader { proxy in
            let metrics = WelcomeLayoutMetrics(
                size: proxy.size,
                theme: theme
            )

            ZStack {
                MultiplayerBrandBackground()
                WelcomeDecorativeCurve()

                WelcomeTitleBlock()
                    .frame(maxWidth: proxy.size.width * 0.78, alignment: .leading)
                    .padding(.leading, metrics.horizontalPadding)
                    .padding(.top, metrics.titleTopPadding)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

                VStack {
                    Spacer(minLength: 0)

                    YandexMusicAuthCardView(
                        viewModel: viewModel
                    )
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, metrics.horizontalPadding)
                    .padding(.bottom, metrics.bottomPadding)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

private struct WelcomeTitleBlock: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.multiplayerTheme) private var theme

    private let titleStyle = MultiplayerTextStyle(
        font: .system(size: 43, weight: .bold, design: .rounded),
        lineSpacing: 2,
        tracking: -1.6
    )
    private let subtitleStyle = MultiplayerTextStyle(
        font: .system(size: 20, weight: .semibold, design: .rounded),
        lineSpacing: 1,
        tracking: -0.35
    )

    private var palette: MultiplayerColors {
        MultiplayerTheme.default(for: colorScheme).colors
    }

    var body: some View {
        VStack(alignment: .leading, spacing: theme.spacing.xxs) {
            MultiplayerText(
                verbatim: "MultiPlayer",
                style: titleStyle,
                color: palette.textPrimary
            )

            MultiplayerText(
                verbatim: "Открытый плеер\nдля стриминговых\nсервисов",
                style: subtitleStyle,
                color: palette.textPrimary.opacity(0.92)
            )
        }
    }
}

private struct WelcomeLayoutMetrics {
    let horizontalPadding: CGFloat
    let titleTopPadding: CGFloat
    let bottomPadding: CGFloat

    init(
        size: CGSize,
        theme: MultiplayerTheme
    ) {
        let contentHeight = size.height
        let proportionalHorizontalPadding = size.width * 0.085

        horizontalPadding = max(proportionalHorizontalPadding, theme.spacing.lg)
        titleTopPadding = contentHeight * 0.48
        bottomPadding = max(contentHeight * 0.05, theme.spacing.lg)
    }
}

private struct WelcomeDecorativeCurve: View {
    @Environment(\.colorScheme) private var colorScheme

    private var palette: MultiplayerColors {
        MultiplayerTheme.default(for: colorScheme).colors
    }

    private var curveFillColor: Color {
        switch colorScheme {
        case .dark:
            palette.brandVisualPrimaryContainer.opacity(0.18)
        default:
            palette.brandVisualPrimary.opacity(0.20)
        }
    }

    var body: some View {
        GeometryReader { proxy in
            historicalCurvePath(in: proxy.size)
                .fill(curveFillColor)
                .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .allowsHitTesting(false)
        .ignoresSafeArea()
    }

    private func historicalCurvePath(in size: CGSize) -> Path {
        Path { path in
            path.move(to: CGPoint(x: 0, y: size.height * 0.444))
            path.addCurve(
                to: CGPoint(x: size.width * 0.454, y: size.height * 0.346),
                control1: CGPoint(x: size.width * 0.17, y: size.height * 0.434),
                control2: CGPoint(x: size.width * 0.288, y: size.height * 0.402)
            )
            path.addCurve(
                to: CGPoint(x: size.width, y: size.height * 0.224),
                control1: CGPoint(x: size.width * 0.58, y: size.height * 0.31),
                control2: CGPoint(x: size.width * 0.722, y: size.height * 0.252)
            )
            path.addLine(to: CGPoint(x: size.width, y: size.height))
            path.addLine(to: CGPoint(x: 0, y: size.height))
            path.closeSubpath()
        }
    }
}


#Preview("Light") {
    MultiplayerDesignSystem(colorScheme: .light) {
        WelcomeView(viewModel: AuthPreviewFactory.makeViewModel())
    }
}

#Preview("Dark") {
    MultiplayerDesignSystem(colorScheme: .dark) {
        WelcomeView(viewModel: AuthPreviewFactory.makeViewModel())
    }
}
