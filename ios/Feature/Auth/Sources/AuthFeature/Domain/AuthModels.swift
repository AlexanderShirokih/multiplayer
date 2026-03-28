import Foundation

public struct YandexAccessToken: RawRepresentable, Codable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }
}

public struct YandexRefreshToken: RawRepresentable, Codable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }
}

public struct YandexClientId: RawRepresentable, Codable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }
}

public struct YandexUserId: RawRepresentable, Codable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }
}

public struct YandexDeviceId: RawRepresentable, Codable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }
}

public struct YandexAuthorizationRequest: Equatable, Sendable {
    public let url: URL

    public init(url: URL) {
        self.url = url
    }
}

public struct YandexUserIdentity: Codable, Equatable, Sendable {
    public let id: YandexUserId
    public let login: String?
    public let displayName: String?
    public let email: String?
    public let avatarId: String?

    public init(
        id: YandexUserId,
        login: String?,
        displayName: String?,
        email: String?,
        avatarId: String?
    ) {
        self.id = id
        self.login = login
        self.displayName = displayName
        self.email = email
        self.avatarId = avatarId
    }
}

public struct YandexAuthSession: Codable, Equatable, Sendable {
    public let accessToken: YandexAccessToken
    public let refreshToken: YandexRefreshToken?
    public let tokenType: String
    public let expiresAt: Date?
    public let scopes: Set<String>
    public let deviceId: YandexDeviceId
    public let user: YandexUserIdentity
    public let clientId: YandexClientId

    public init(
        accessToken: YandexAccessToken,
        refreshToken: YandexRefreshToken?,
        tokenType: String,
        expiresAt: Date?,
        scopes: Set<String>,
        deviceId: YandexDeviceId,
        user: YandexUserIdentity,
        clientId: YandexClientId
    ) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.tokenType = tokenType
        self.expiresAt = expiresAt
        self.scopes = scopes
        self.deviceId = deviceId
        self.user = user
        self.clientId = clientId
    }
}

public enum YandexAuthStatus: Equatable, Sendable {
    case unauthorized
    case authorizing
    case authorized(YandexAuthSession)
    case failed(YandexAuthException)
}

public enum YandexAuthException: Error, Equatable, LocalizedError, Sendable {
    case missingConfiguration
    case missingSession
    case missingPendingAuthorization
    case missingAuthorizationCode
    case invalidCallbackState
    case accessDenied(description: String?)
    case providerError(code: String, description: String?)
    case networkFailure(reason: String)
    case refreshFailed(reason: String)
    case storageFailure(reason: String)

    public var errorDescription: String? {
        switch self {
        case .missingConfiguration:
            return "Yandex OAuth configuration is missing."

        case .missingSession:
            return "Yandex session is missing."

        case .missingPendingAuthorization:
            return "Pending Yandex authorization request is missing."

        case .missingAuthorizationCode:
            return "Yandex OAuth callback does not contain an authorization code."

        case .invalidCallbackState:
            return "Yandex OAuth callback state is invalid."

        case let .accessDenied(description):
            return description ?? "Yandex OAuth access was denied."

        case let .providerError(code, description):
            return description ?? code

        case let .networkFailure(reason):
            return reason

        case let .refreshFailed(reason):
            return reason

        case let .storageFailure(reason):
            return reason
        }
    }
}

public enum AuthorizedMusicProvider: String, Equatable, Sendable {
    case yandexMusic
}

public protocol MusicProviderAuthorizationRepository: Sendable {
    func currentAuthorizedProvider() -> AuthorizedMusicProvider?

    func observeAuthorizedProvider() -> AsyncStream<AuthorizedMusicProvider?>
}

public protocol YandexAuthRepository: Sendable {
    func currentSession() -> YandexAuthSession?

    func observeSession() -> AsyncStream<YandexAuthSession?>

    func observeStatus() -> AsyncStream<YandexAuthStatus>

    func createAuthorizationRequest() async throws -> YandexAuthorizationRequest

    func completeAuthorization(callbackURL: URL) async throws -> YandexAuthSession

    func cancelAuthorization() async

    func logout() async
}

public protocol YandexAccessTokenProvider: Sendable {
    func validAccessToken(forceRefresh: Bool) async throws -> String
}
