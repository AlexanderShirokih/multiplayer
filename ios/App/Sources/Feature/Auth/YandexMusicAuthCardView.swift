import AuthFeature
import CoreUI
import SwiftUI

struct YandexMusicAuthCardView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.multiplayerTheme) private var theme
    @Environment(\.openURL) private var openURL

    let viewModel: YandexMusicAuthCardViewModel

    @State private var alertMessage: String?

    private var palette: MultiplayerColors {
        MultiplayerTheme.default(for: colorScheme).colors
    }

    private var buttonTextColor: Color {
        colorScheme == .dark ? palette.textInverse : palette.textPrimary
    }

    private var buttonTitle: String {
        if viewModel.state.isAuthorized {
            return "Яндекс Музыка подключена"
        }
        return "Войти через Яндекс Музыку"
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
                    style: theme.typography.title,
                    color: palette.textPrimary
                )

                MultiplayerText(
                    verbatim: viewModel.state.isAuthorized
                        ? "Аккаунт подключён. Навигация переключится на плеер автоматически."
                        : "Вход по OAuth через Yandex ID,\nчтобы сразу перейти к своей музыке.",
                    style: theme.typography.meta,
                    color: palette.textSecondary
                )

                Button {
                    Task {
                        await viewModel.onLoginTapped()
                    }
                } label: {
                    ZStack {
                        MultiplayerText(
                            verbatim: buttonTitle,
                            style: theme.typography.label,
                            color: buttonTextColor,
                            alignment: .center,
                            lineLimit: 1
                        )
                        .opacity(viewModel.state.isLoading ? 0 : 1)

                        if viewModel.state.isLoading {
                            ProgressView()
                                .tint(buttonTextColor)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, theme.spacing.lg)
                    .padding(.vertical, theme.spacing.md)
                    .background(
                        LinearGradient(
                            colors: palette.ctaGradient.colors,
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(viewModel.state.isLoading || viewModel.state.isAuthorized)
                .opacity(viewModel.state.isLoading || viewModel.state.isAuthorized ? 0.72 : 1)
                .padding(.top, theme.spacing.xxs)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .task {
            let effectStream = viewModel.effectStream()
            for await effect in effectStream {
                switch effect {
                case let .openExternalAuth(url):
                    openURL(url) { accepted in
                        if !accepted {
                            viewModel.onBrowserLaunchFailed()
                        }
                    }
                case let .showAlert(message):
                    alertMessage = message
                }
            }
        }
        .alert(
            "Ошибка авторизации",
            isPresented: Binding(
                get: { alertMessage != nil },
                set: { isPresented in
                    if !isPresented {
                        alertMessage = nil
                    }
                }
            )
        ) {
            Button("OK", role: .cancel) {
                alertMessage = nil
            }
        } message: {
            Text(alertMessage ?? "")
        }
    }
}

#Preview("Card Light") {
    MultiplayerDesignSystem(colorScheme: .light) {
        YandexMusicAuthCardView(viewModel: AuthPreviewFactory.makeViewModel())
            .padding(24)
    }
}

#Preview("Card Dark") {
    MultiplayerDesignSystem(colorScheme: .dark) {
        YandexMusicAuthCardView(viewModel: AuthPreviewFactory.makeViewModel())
            .padding(24)
    }
}

enum AuthPreviewFactory {
    @MainActor
    static func makeViewModel(
        isAuthorized: Bool = false
    ) -> YandexMusicAuthCardViewModel {
        let repository = PreviewYandexAuthRepository(isAuthorized: isAuthorized)
        let viewModel = YandexMusicAuthCardViewModel(
            startYandexAuthorization: StartYandexAuthorizationUseCase(repository: repository),
            cancelYandexAuthorization: CancelYandexAuthorizationUseCase(repository: repository),
            observeYandexSession: ObserveYandexSessionUseCase(repository: repository),
            observeYandexAuthStatus: ObserveYandexAuthStatusUseCase(repository: repository)
        )
        viewModel.start()
        return viewModel
    }
}

private final class PreviewYandexAuthRepository: YandexAuthRepository, @unchecked Sendable {
    private let previewSession: YandexAuthSession?
    private let previewStatus: YandexAuthStatus

    init(isAuthorized: Bool) {
        if isAuthorized {
            let session = YandexAuthSession(
                accessToken: YandexAccessToken(rawValue: "preview-token"),
                refreshToken: nil,
                tokenType: "bearer",
                expiresAt: nil,
                scopes: [],
                deviceId: YandexDeviceId(rawValue: "preview-device"),
                user: YandexUserIdentity(
                    id: YandexUserId(rawValue: "preview-user"),
                    login: "preview",
                    displayName: "Preview User",
                    email: nil,
                    avatarId: nil
                ),
                clientId: YandexClientId(rawValue: "preview-client")
            )
            previewSession = session
            previewStatus = .authorized(session)
        } else {
            previewSession = nil
            previewStatus = .unauthorized
        }
    }

    func currentSession() -> YandexAuthSession? {
        previewSession
    }

    func observeSession() -> AsyncStream<YandexAuthSession?> {
        AsyncStream { continuation in
            continuation.yield(previewSession)
            continuation.finish()
        }
    }

    func observeStatus() -> AsyncStream<YandexAuthStatus> {
        AsyncStream { continuation in
            continuation.yield(previewStatus)
            continuation.finish()
        }
    }

    func createAuthorizationRequest() async throws -> YandexAuthorizationRequest {
        return YandexAuthorizationRequest(url: URL(string: "https://oauth.yandex.ru/authorize")!)
    }

    func completeAuthorization(callbackURL: URL) async throws -> YandexAuthSession {
        throw YandexAuthException.missingAuthorizationCode
    }

    func cancelAuthorization() async {}

    func logout() async {
    }
}
