import AuthFeature
import CoreDataLayer
import CoreDomain
import CorePlayer
import DeviceMusicService
import Foundation
import LibraryFeature
import Observation
import ServicesKitharaPlayer
import UIKit
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
    private var userOptedIntoDeviceOnly = false

    init() {
        let dependencies = AppDependencies.live()
        authCardViewModel = dependencies.authCardViewModel
        musicLibraryViewModel = dependencies.musicLibraryViewModel
        makeTrackListViewModel = dependencies.makeTrackListViewModel
        resetAuthorization = dependencies.resetAuthorization
        observeAuthorizedMusicProvider = dependencies.observeAuthorizedMusicProvider
        destination = Self.destination(
            provider: dependencies.observeAuthorizedMusicProvider.currentAuthorizedProvider(),
            userOptedIntoDeviceOnly: false
        )
    }

    func openDeviceOnlyMode() {
        userOptedIntoDeviceOnly = true
        destination = .library
    }

    func start() {
        guard observationTask == nil else { return }

        authCardViewModel.start()
        observationTask = Task { [weak self] in
            guard let self else { return }

            for await provider in observeAuthorizedMusicProvider() {
                if provider != nil {
                    userOptedIntoDeviceOnly = false
                }
                destination = Self.destination(
                    provider: provider,
                    userOptedIntoDeviceOnly: userOptedIntoDeviceOnly
                )
            }
        }
    }

    private static func destination(
        provider: AuthorizedMusicProvider?,
        userOptedIntoDeviceOnly: Bool
    ) -> Destination {
        if provider != nil || userOptedIntoDeviceOnly {
            return .library
        }
        return .auth
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
        let makeTrackListViewModel = makeTrackListViewModelFactory(musicServices: musicServices)

        return Self(
            observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase(repository: authRepository),
            authCardViewModel: makeAuthCardViewModel(authRepository: authRepository),
            musicLibraryViewModel: MusicLibraryViewModel(
                observeOwnPlaylists: ObserveOwnPlaylistsUseCase(library: musicServices.library),
                refreshLibrary: RefreshLibraryUseCase(library: musicServices.library)
            ),
            makeTrackListViewModel: makeTrackListViewModel,
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
        let deviceServices = DeviceMusicServiceModule.makeServices()
        let yandexMusicAPI = URLSessionYandexMusicAPI(
            streamingConfig: bundle.yandexMusicStreamingConfig
        )
        let yandexProvider = YandexMusicProvider(
            accessTokenProvider: authRepository,
            api: yandexMusicAPI
        )
        let trackStreamURLProvider = YandexTrackStreamUrlProvider(
            accessTokenProvider: authRepository,
            api: yandexMusicAPI
        )
        let library = DefaultMusicLibrary(
            providers: [deviceServices.provider, yandexProvider]
        )
        let urlResolver = CompositePlayableUrlResolver(
            providers: [
                .device: deviceServices.urlProvider,
                .yandexMusic: trackStreamURLProvider
            ]
        )
        let playbackQueueBridge = ServicesKitharaPlayerModule.makePlaybackQueueBridge(
            urlResolver: urlResolver
        )
        return MusicServices(
            library: library,
            playbackQueueBridge: playbackQueueBridge,
            deviceAuthorizationController: deviceServices.authorizationController
        )
    }

    @MainActor
    private static func makeTrackListViewModelFactory(
        musicServices: MusicServices
    ) -> (MusicLibraryDestination) -> TrackListViewModel {
        { destination in
            TrackListViewModel(
                destination: destination,
                observePlaylist: ObservePlaylistUseCase(library: musicServices.library),
                refreshPlaylist: RefreshPlaylistUseCase(library: musicServices.library),
                observeSavedTracks: ObserveSavedTracksUseCase(library: musicServices.library),
                refreshSavedTracks: RefreshSavedTracksUseCase(library: musicServices.library),
                playbackBridge: musicServices.playbackQueueBridge,
                requestDeviceMediaAccess: RequestDeviceMediaAccessUseCase(
                    controller: musicServices.deviceAuthorizationController
                ),
                readDeviceMediaAccessStatus: ReadDeviceMediaAccessStatusUseCase(
                    controller: musicServices.deviceAuthorizationController
                ),
                openSystemSettings: openAppSettings
            )
        }
    }

    @MainActor
    private static func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else {
            return
        }
        UIApplication.shared.open(url)
    }
}

private struct MusicServices {
    let library: MusicLibrary
    let playbackQueueBridge: PlaybackQueueBridge
    let deviceAuthorizationController: DeviceMediaAuthorizationController
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
