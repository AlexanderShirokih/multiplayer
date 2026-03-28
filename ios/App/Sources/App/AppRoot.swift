import AuthFeature
import Foundation
import LibraryFeature
import Observation
import YandexMusicService

@Observable
@MainActor
final class AppRoot {
    enum Destination: Hashable {
        case auth
        case library
    }

    let authCardViewModel: YandexMusicAuthCardViewModel
    let musicLibraryViewModel: MusicLibraryViewModel

    var destination: Destination

    private let observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase
    private let completeYandexAuthorization: CompleteYandexAuthorizationUseCase
    private let redirectURL: URL
    private var observationTask: Task<Void, Never>?
    private var isHandlingAuthorizationCallback = false

    init() {
        let dependencies = AppDependencies.live()
        authCardViewModel = dependencies.authCardViewModel
        musicLibraryViewModel = dependencies.musicLibraryViewModel
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
    let musicLibraryViewModel: MusicLibraryViewModel

    @MainActor
    static func live(bundle: Bundle = .main) -> AppDependencies {
        let oauthConfig = bundle.yandexOAuthConfig
        let secureStore = KeychainSecureStore(
            service: "\(bundle.bundleIdentifier ?? "com.mplayeraudio").auth"
        )
        let sessionStore = KeychainYandexSessionStore(secureStore: secureStore)
        let authRepository = YandexAuthRepositoryImpl(
            config: oauthConfig,
            sessionStore: sessionStore,
            secureStore: secureStore
        )
        let musicLibraryRepository = YandexMusicRepositoryImpl(
            accessTokenProvider: authRepository
        )

        return AppDependencies(
            oauthConfig: oauthConfig,
            observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase(repository: authRepository),
            completeYandexAuthorization: CompleteYandexAuthorizationUseCase(repository: authRepository),
            authCardViewModel: YandexMusicAuthCardViewModel(
                startYandexAuthorization: StartYandexAuthorizationUseCase(repository: authRepository),
                cancelYandexAuthorization: CancelYandexAuthorizationUseCase(repository: authRepository),
                observeYandexSession: ObserveYandexSessionUseCase(repository: authRepository),
                observeYandexAuthStatus: ObserveYandexAuthStatusUseCase(repository: authRepository)
            ),
            musicLibraryViewModel: MusicLibraryViewModel(
                observeOwnPlaylists: ObserveOwnPlaylistsUseCase(repository: musicLibraryRepository),
                refreshLibrary: RefreshLibraryUseCase(repository: musicLibraryRepository)
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
            return .library
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
