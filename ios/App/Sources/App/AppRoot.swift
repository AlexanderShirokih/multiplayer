import AuthFeature
import CorePlayer
import Foundation
import LibraryFeature
import Observation
import ServicesKitharaPlayer
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
    let makeTrackListViewModel: (MusicLibraryDestination) -> TrackListViewModel
    let resetAuthorization: @MainActor @Sendable () async -> Void

    var destination: Destination

    private let observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase
    private var observationTask: Task<Void, Never>?

    init() {
        let dependencies = AppDependencies.live()
        authCardViewModel = dependencies.authCardViewModel
        musicLibraryViewModel = dependencies.musicLibraryViewModel
        makeTrackListViewModel = dependencies.makeTrackListViewModel
        resetAuthorization = dependencies.resetAuthorization
        observeAuthorizedMusicProvider = dependencies.observeAuthorizedMusicProvider
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
}

private struct AppDependencies {
    let observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase
    let authCardViewModel: YandexMusicAuthCardViewModel
    let musicLibraryViewModel: MusicLibraryViewModel
    let makeTrackListViewModel: (MusicLibraryDestination) -> TrackListViewModel
    let resetAuthorization: @MainActor @Sendable () async -> Void

    @MainActor
    static func live(bundle: Bundle = .main) -> Self {
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
        let logoutYandexAuthorization = LogoutYandexAuthorizationUseCase(repository: authRepository)
        let musicServices = makeMusicServices(
            bundle: bundle,
            authRepository: authRepository
        )

        return Self(
            observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase(repository: authRepository),
            authCardViewModel: makeAuthCardViewModel(authRepository: authRepository),
            musicLibraryViewModel: MusicLibraryViewModel(
                observeOwnPlaylists: ObserveOwnPlaylistsUseCase(repository: musicServices.repository),
                refreshLibrary: RefreshLibraryUseCase(repository: musicServices.repository)
            ),
            makeTrackListViewModel: { destination in
                TrackListViewModel(
                    destination: destination,
                    observePlaylist: ObservePlaylistUseCase(repository: musicServices.repository),
                    refreshPlaylist: RefreshPlaylistUseCase(repository: musicServices.repository),
                    observeSavedTracks: ObserveSavedTracksUseCase(repository: musicServices.repository),
                    refreshSavedTracks: RefreshSavedTracksUseCase(repository: musicServices.repository),
                    playbackBridge: musicServices.playbackQueueBridge
                )
            },
            resetAuthorization: {
                await logoutYandexAuthorization()
            }
        )
    }

    @MainActor
    private static func makeAuthCardViewModel(
        authRepository: YandexAuthRepositoryImpl
    ) -> YandexMusicAuthCardViewModel {
        YandexMusicAuthCardViewModel(
            startYandexAuthorization: StartYandexAuthorizationUseCase(repository: authRepository),
            completeYandexAuthorization: CompleteYandexAuthorizationUseCase(repository: authRepository),
            cancelYandexAuthorization: CancelYandexAuthorizationUseCase(repository: authRepository),
            observeYandexSession: ObserveYandexSessionUseCase(repository: authRepository),
            observeYandexAuthStatus: ObserveYandexAuthStatusUseCase(repository: authRepository)
        )
    }

    @MainActor
    private static func makeMusicServices(
        bundle: Bundle,
        authRepository: YandexAuthRepositoryImpl
    ) -> MusicServices {
        let yandexMusicAPI = URLSessionYandexMusicAPI(
            streamingConfig: bundle.yandexMusicStreamingConfig
        )
        let repository = YandexMusicRepositoryImpl(
            accessTokenProvider: authRepository,
            api: yandexMusicAPI
        )
        let trackStreamURLProvider = YandexTrackStreamUrlProvider(
            accessTokenProvider: authRepository,
            api: yandexMusicAPI
        )
        let playbackQueueBridge = ServicesKitharaPlayerModule.makePlaybackQueueBridge(
            urlProvider: trackStreamURLProvider
        )
        return MusicServices(
            repository: repository,
            playbackQueueBridge: playbackQueueBridge
        )
    }
}

private struct MusicServices {
    let repository: YandexMusicRepositoryImpl
    let playbackQueueBridge: PlaybackQueueBridge
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

private extension Bundle {
    var yandexOAuthConfig: YandexOAuthConfig {
        let info = infoDictionary ?? [:]
        let clientId = (info["YandexAuthClientID"] as? String) ?? ""
        let authorizationRedirectURL = URL(string: "https://music.yandex.ru/")!
        return YandexOAuthConfig(
            clientId: YandexClientId(rawValue: clientId),
            authorizationRedirectURL: authorizationRedirectURL
        )
    }

    var yandexMusicStreamingConfig: YandexMusicStreamingConfig {
        let info = infoDictionary ?? [:]
        return YandexMusicStreamingConfig(
            downloadInfoSigningSecret: (info["YandexMusicStreamingSecret"] as? String) ?? "",
            downloadInfoClientHeader: (info["YandexMusicStreamingClient"] as? String)
                ?? "YandexMusicAndroid/24022571",
            fileInfoSigningSecret: (info["YandexMusicFileInfoSecret"] as? String) ?? "",
            fileInfoClientHeader: (info["YandexMusicFileInfoClient"] as? String)
                ?? "YandexMusicDesktopAppWindows/5.13.2"
        )
    }
}
