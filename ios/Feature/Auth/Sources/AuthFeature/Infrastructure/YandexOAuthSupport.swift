import CryptoKit
import Foundation

public struct YandexOAuthConfig: Sendable {
    public let clientId: YandexClientId
    public let clientSecret: String
    public let redirectURL: URL
    public let deviceName: String?

    public init(
        clientId: YandexClientId,
        clientSecret: String,
        redirectURL: URL,
        deviceName: String? = nil
    ) {
        self.clientId = clientId
        self.clientSecret = clientSecret
        self.redirectURL = redirectURL
        self.deviceName = deviceName
    }

    public func requireAuthorizationConfig() throws {
        guard !clientId.rawValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw YandexAuthException.missingConfiguration
        }
    }

    public func requireRefreshConfig() throws {
        try requireAuthorizationConfig()
        guard !clientSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw YandexAuthException.missingConfiguration
        }
    }
}

struct PendingYandexAuthorization: Codable, Equatable, Sendable {
    let state: String
    let codeVerifier: String
    let deviceId: YandexDeviceId
    let deviceName: String
    let requestedAt: Date
}

public struct OAuthTokenPayload: Equatable, Sendable {
    public let tokenType: String
    public let accessToken: YandexAccessToken
    public let refreshToken: YandexRefreshToken?
    public let expiresInSeconds: Int?
    public let scopes: Set<String>

    public init(
        tokenType: String,
        accessToken: YandexAccessToken,
        refreshToken: YandexRefreshToken?,
        expiresInSeconds: Int?,
        scopes: Set<String>
    ) {
        self.tokenType = tokenType
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.expiresInSeconds = expiresInSeconds
        self.scopes = scopes
    }
}

struct ParsedAuthorizationCallback: Equatable, Sendable {
    let code: String?
    let state: String?
    let error: String?
    let errorDescription: String?
}

struct PkcePayload: Equatable, Sendable {
    let verifier: String
    let challenge: String
    let state: String
}

protocol YandexPendingAuthorizationStore: Sendable {
    func get() async throws -> PendingYandexAuthorization?

    func save(_ authorization: PendingYandexAuthorization) async throws

    func clear() async throws
}

public protocol YandexSessionStore: Sendable {
    var cachedSession: YandexAuthSession? { get }

    func observeSession() -> AsyncStream<YandexAuthSession?>

    func save(_ session: YandexAuthSession) async throws

    func clear() async throws
}

protocol YandexDeviceMetadataProviding: Sendable {
    func deviceId() async throws -> YandexDeviceId

    func deviceName() -> String
}

protocol PkceGenerating: Sendable {
    func generate() throws -> PkcePayload
}

protocol YandexAuthorizationURLBuilding: Sendable {
    func buildAuthorizationURL(
        config: YandexOAuthConfig,
        state: String,
        codeChallenge: String,
        deviceId: YandexDeviceId,
        deviceName: String
    ) throws -> URL
}

protocol YandexAuthorizationCallbackParsing: Sendable {
    func parse(_ callbackURL: URL) -> ParsedAuthorizationCallback
}

struct YandexAuthorizationURLBuilder: YandexAuthorizationURLBuilding {
    func buildAuthorizationURL(
        config: YandexOAuthConfig,
        state: String,
        codeChallenge: String,
        deviceId: YandexDeviceId,
        deviceName: String
    ) throws -> URL {
        guard var components = URLComponents(string: "https://oauth.yandex.ru/authorize") else {
            throw YandexAuthException.missingConfiguration
        }
        components.queryItems = [
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: config.clientId.rawValue),
            URLQueryItem(name: "redirect_uri", value: config.redirectURL.absoluteString),
            URLQueryItem(name: "device_id", value: deviceId.rawValue),
            URLQueryItem(name: "device_name", value: deviceName),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
        ]

        guard let url = components.url else {
            throw YandexAuthException.missingConfiguration
        }
        return url
    }
}

struct YandexAuthorizationCallbackParser: YandexAuthorizationCallbackParsing {
    func parse(_ callbackURL: URL) -> ParsedAuthorizationCallback {
        let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)
        let queryItems = components?.queryItems ?? []

        func value(for key: String) -> String? {
            queryItems.first(where: { $0.name == key })?.value
        }

        return ParsedAuthorizationCallback(
            code: value(for: "code"),
            state: value(for: "state"),
            error: value(for: "error"),
            errorDescription: value(for: "error_description")
        )
    }
}

struct SecurePkceGenerator: PkceGenerating {
    func generate() throws -> PkcePayload {
        let verifier = try randomURLSafeString(byteCount: 32)
        let state = try randomURLSafeString(byteCount: 32)
        let challengeData = Data(SHA256.hash(data: Data(verifier.utf8)))
        let challenge = challengeData.base64URLEncodedString()

        return PkcePayload(
            verifier: verifier,
            challenge: challenge,
            state: state
        )
    }

    private func randomURLSafeString(byteCount: Int) throws -> String {
        var bytes = [UInt8](repeating: 0, count: byteCount)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else {
            throw YandexAuthException.storageFailure(reason: "Unable to generate secure random bytes.")
        }

        return Data(bytes).base64URLEncodedString()
    }
}

public final class AppleDeviceMetadataProvider: YandexDeviceMetadataProviding, @unchecked Sendable {
    private let configuredName: String?
    private let deviceIdKey: String
    private let secureStore: SecureKeyValueStore

    public init(
        configuredName: String?,
        secureStore: SecureKeyValueStore,
        deviceIdKey: String = "yandex.oauth.device-id"
    ) {
        self.configuredName = configuredName
        self.secureStore = secureStore
        self.deviceIdKey = deviceIdKey
    }

    public func deviceId() async throws -> YandexDeviceId {
        if let data = try secureStore.data(forKey: deviceIdKey),
           let rawValue = String(data: data, encoding: .utf8),
           !rawValue.isEmpty {
            return YandexDeviceId(rawValue: rawValue)
        }

        let generatedId = UUID().uuidString
        do {
            try secureStore.set(Data(generatedId.utf8), forKey: deviceIdKey)
        } catch let error as YandexAuthException {
            throw error
        } catch {
            throw YandexAuthException.storageFailure(reason: error.localizedDescription)
        }
        return YandexDeviceId(rawValue: generatedId)
    }

    public func deviceName() -> String {
        let trimmedName = configuredName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedName.isEmpty {
            return String(trimmedName.prefix(100))
        }

        let systemVersion = ProcessInfo.processInfo.operatingSystemVersion
        return "MultiPlayer iOS \(systemVersion.majorVersion)"
    }
}

struct YandexTokenRefresher: Sendable {
    private let oauthAPI: YandexOAuthAPI
    private let config: YandexOAuthConfig
    private let now: @Sendable () -> Date
    private let refreshSafetyWindow: TimeInterval

    init(
        oauthAPI: YandexOAuthAPI,
        config: YandexOAuthConfig,
        now: @escaping @Sendable () -> Date,
        refreshSafetyWindow: TimeInterval = 5 * 60
    ) {
        self.oauthAPI = oauthAPI
        self.config = config
        self.now = now
        self.refreshSafetyWindow = refreshSafetyWindow
    }

    func shouldRefresh(_ session: YandexAuthSession) -> Bool {
        guard let expiresAt = session.expiresAt else { return false }
        return expiresAt <= now().addingTimeInterval(refreshSafetyWindow)
    }

    func refresh(_ session: YandexAuthSession) async throws -> YandexAuthSession {
        guard let refreshToken = session.refreshToken else {
            throw YandexAuthException.refreshFailed(reason: "Yandex refresh token is missing.")
        }

        do {
            let payload = try await oauthAPI.refreshAccessToken(
                config: config,
                refreshToken: refreshToken
            )
            return YandexAuthSession(
                accessToken: payload.accessToken,
                refreshToken: payload.refreshToken ?? refreshToken,
                tokenType: payload.tokenType,
                expiresAt: payload.expiresInSeconds.map { now().addingTimeInterval(TimeInterval($0)) },
                scopes: payload.scopes.isEmpty ? session.scopes : payload.scopes,
                deviceId: session.deviceId,
                user: session.user,
                clientId: session.clientId
            )
        } catch let error as YandexAuthException {
            throw YandexAuthException.refreshFailed(
                reason: error.errorDescription ?? "Unable to refresh Yandex OAuth token."
            )
        } catch {
            throw YandexAuthException.refreshFailed(reason: error.localizedDescription)
        }
    }
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}
