import CoreDomain
import Foundation
import MediaPlayer

public final class DeviceMediaAuthorizationAdapter: DeviceMediaAuthorizationController, @unchecked Sendable {
    public init() {}

    public func authorizationStatus() -> DeviceMediaAuthorizationStatus {
        Self.map(status: MPMediaLibrary.authorizationStatus())
    }

    public func requestAuthorization() async -> DeviceMediaAuthorizationStatus {
        Self.map(status: await MPMediaLibrary.requestAuthorization())
    }

    private static func map(status: MPMediaLibraryAuthorizationStatus) -> DeviceMediaAuthorizationStatus {
        switch status {
        case .authorized:
            return .authorized

        case .denied:
            return .denied

        case .restricted:
            return .restricted

        case .notDetermined:
            return .notDetermined

        @unknown default:
            return .denied
        }
    }
}
