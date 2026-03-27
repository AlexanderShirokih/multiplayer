import AuthFeature
import Foundation
import Observation

@Observable
@MainActor
final class AppRoot {
    enum Destination: Hashable {
        case auth
        case player
    }

    let authCardViewModel: YandexMusicAuthCardViewModel

    var destination: Destination

    private let observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase
    private let completeYandexAuthorization: CompleteYandexAuthorizationUseCase
    private let redirectURL: URL
    private var observationTask: Task<Void, Never>?
    private var isHandlingAuthorizationCallback = false

    init() {
        let dependencies = AppDependencies.live()
        authCardViewModel = dependencies.authCardViewModel
        observeAuthorizedMusicProvider = dependencies.observeAuthorizedMusicProvider
        completeYandexAuthorization = dependencies.completeYandexAuthorization
        redirectURL = dependencies.oauthConfig.redirectURL
        destination = dependencies.observeAuthorizedMusicProvider.currentAuthorizedProvider().toDestination()
    }

    func start() {
        guard observationTask == nil else { return }

        authCardViewModel.start()
        observationTask = Task { [weak self] in
            guard let self else { return }

            for await provider in observeAuthorizedMusicProvider() {
                destination = provider.toDestination()
            }
        }
    }

    func handleIncomingURL(_ url: URL) async {
        guard url.normalizedOAuthURL == redirectURL.normalizedOAuthURL else {
            return
        }

        isHandlingAuthorizationCallback = true
        defer { isHandlingAuthorizationCallback = false }
        _ = try? await completeYandexAuthorization(callbackURL: url)
    }

    func handleAppDidBecomeActive() async {
        guard !isHandlingAuthorizationCallback else {
            return
        }

        await authCardViewModel.onAuthorizationFlowReturnedWithoutCallback()
    }
}

private struct AppDependencies {
    let oauthConfig: YandexOAuthConfig
    let observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase
    let completeYandexAuthorization: CompleteYandexAuthorizationUseCase
    let authCardViewModel: YandexMusicAuthCardViewModel

    @MainActor
    static func live(bundle: Bundle = .main) -> AppDependencies {
        let oauthConfig = bundle.yandexOAuthConfig
        let secureStore = KeychainSecureStore(
            service: "\(bundle.bundleIdentifier ?? "com.mplayeraudio").auth"
        )
        let sessionStore = KeychainYandexSessionStore(secureStore: secureStore)
        let repository = YandexAuthRepositoryImpl(
            config: oauthConfig,
            sessionStore: sessionStore,
            secureStore: secureStore
        )

        return AppDependencies(
            oauthConfig: oauthConfig,
            observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase(repository: repository),
            completeYandexAuthorization: CompleteYandexAuthorizationUseCase(repository: repository),
            authCardViewModel: YandexMusicAuthCardViewModel(
                startYandexAuthorization: StartYandexAuthorizationUseCase(repository: repository),
                cancelYandexAuthorization: CancelYandexAuthorizationUseCase(repository: repository),
                observeYandexSession: ObserveYandexSessionUseCase(repository: repository),
                observeYandexAuthStatus: ObserveYandexAuthStatusUseCase(repository: repository)
            )
        )
    }
}

private extension AuthorizedMusicProvider? {
    func toDestination() -> AppRoot.Destination {
        switch self {
        case nil:
            return .auth
        case .some:
            return .player
        }
    }
}

private extension URL {
    var normalizedOAuthURL: String {
        var components = URLComponents(url: self, resolvingAgainstBaseURL: false)
        components?.query = nil
        components?.fragment = nil
        return components?.string ?? absoluteString
    }
}

private extension Bundle {
    var yandexOAuthConfig: YandexOAuthConfig {
        let info = infoDictionary ?? [:]
        let clientId = (info["YandexOAuthClientID"] as? String) ?? ""
        let clientSecret = (info["YandexOAuthClientSecret"] as? String) ?? ""
        let scheme = ((info["YandexOAuthRedirectScheme"] as? String) ?? "mplayeraudio")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let host = ((info["YandexOAuthRedirectHost"] as? String) ?? "oauth")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let path = ((info["YandexOAuthRedirectPath"] as? String) ?? "/yandex")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let deviceName = (info["YandexOAuthDeviceName"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)

        var components = URLComponents()
        components.scheme = scheme.isEmpty ? "mplayeraudio" : scheme
        components.host = host.isEmpty ? "oauth" : host
        components.path = path.isEmpty ? "/yandex" : path

        let redirectURL = components.url ?? URL(string: "mplayeraudio://oauth/yandex")!
        return YandexOAuthConfig(
            clientId: YandexClientId(rawValue: clientId),
            clientSecret: clientSecret,
            redirectURL: redirectURL,
            deviceName: deviceName
        )
    }
}
