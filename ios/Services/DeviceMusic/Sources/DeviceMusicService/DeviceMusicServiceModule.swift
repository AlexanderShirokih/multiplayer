import CoreDomain
import Foundation

public enum DeviceMusicServiceModule {
    @MainActor
    public static func makeServices() -> DeviceMusicServices {
        let authorizationController = DeviceMediaAuthorizationAdapter()
        let mediaLibrary = SystemDeviceMediaLibrary()
        let documentsLibrary = SystemDocumentsAudioLibrary()
        let provider = DeviceMusicProvider(
            authorizationController: authorizationController,
            mediaLibrary: mediaLibrary,
            documentsLibrary: documentsLibrary
        )
        let urlProvider = DeviceTrackStreamUrlProvider(provider: provider)
        return DeviceMusicServices(
            authorizationController: authorizationController,
            provider: provider,
            urlProvider: urlProvider
        )
    }
}

public struct DeviceMusicServices: Sendable {
    public let authorizationController: DeviceMediaAuthorizationController
    public let provider: DeviceMusicProvider
    public let urlProvider: DeviceTrackStreamUrlProvider

    public init(
        authorizationController: DeviceMediaAuthorizationController,
        provider: DeviceMusicProvider,
        urlProvider: DeviceTrackStreamUrlProvider
    ) {
        self.authorizationController = authorizationController
        self.provider = provider
        self.urlProvider = urlProvider
    }
}
