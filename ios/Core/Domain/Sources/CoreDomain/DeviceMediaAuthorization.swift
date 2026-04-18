import Foundation

public enum DeviceMediaAuthorizationStatus: Sendable, Equatable {
    case notDetermined
    case denied
    case restricted
    case authorized
}

public protocol DeviceMediaAuthorizationController: Sendable {
    func authorizationStatus() -> DeviceMediaAuthorizationStatus
    func requestAuthorization() async -> DeviceMediaAuthorizationStatus
}
