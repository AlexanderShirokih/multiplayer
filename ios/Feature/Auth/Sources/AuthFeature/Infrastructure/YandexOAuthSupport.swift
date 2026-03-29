import Foundation

public struct YandexOAuthConfig: Sendable {
    public let clientId: YandexClientId
    public let authorizationRedirectURL: URL

    public init(
        clientId: YandexClientId,
        authorizationRedirectURL: URL
    ) {
        self.clientId = clientId
        self.authorizationRedirectURL = authorizationRedirectURL
    }

    public func requireAuthorizationConfig() throws {
        guard !clientId.rawValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw YandexAuthException.missingConfiguration
        }
    }
}

struct PendingYandexAuthorization: Codable, Equatable, Sendable {
    let state: String
    let deviceId: YandexDeviceId
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
    let accessToken: YandexAccessToken?
    let tokenType: String?
    let expiresInSeconds: Int?
    let scopes: Set<String>
}

struct AuthorizationStatePayload: Equatable, Sendable {
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
}

protocol AuthorizationStateGenerating: Sendable {
    func generate() throws -> AuthorizationStatePayload
}

protocol YandexAuthorizationURLBuilding: Sendable {
    func buildAuthorizationURL(
        config: YandexOAuthConfig,
        state: String
    ) throws -> URL
}

protocol YandexAuthorizationCallbackParsing: Sendable {
    func parse(_ callbackURL: URL) -> ParsedAuthorizationCallback
}

struct YandexAuthorizationURLBuilder: YandexAuthorizationURLBuilding {
    func buildAuthorizationURL(
        config: YandexOAuthConfig,
        state: String
    ) throws -> URL {
        guard var components = URLComponents(string: "https://oauth.yandex.ru/authorize") else {
            throw YandexAuthException.missingConfiguration
        }
        components.queryItems = [
            URLQueryItem(name: "response_type", value: "token"),
            URLQueryItem(name: "client_id", value: config.clientId.rawValue),
            URLQueryItem(name: "redirect_uri", value: config.authorizationRedirectURL.absoluteString),
            URLQueryItem(name: "state", value: state)
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
        let queryItems = dictionary(from: components?.queryItems ?? [])
        let fragmentItems = dictionary(fromFragment: callbackURL.fragment)

        func value(for key: String) -> String? {
            queryItems[key] ?? fragmentItems[key]
        }

        return ParsedAuthorizationCallback(
            code: value(for: "code"),
            state: value(for: "state"),
            error: value(for: "error"),
            errorDescription: value(for: "error_description"),
            accessToken: value(for: "access_token").map(YandexAccessToken.init(rawValue:)),
            tokenType: value(for: "token_type"),
            expiresInSeconds: value(for: "expires_in").flatMap(Int.init),
            scopes: parseScopes(value(for: "scope"))
        )
    }

    private func dictionary(from queryItems: [URLQueryItem]) -> [String: String] {
        queryItems.reduce(into: [:]) { result, item in
            guard !item.name.isEmpty else {
                return
            }
            result[item.name] = item.value ?? ""
        }
    }

    private func dictionary(fromFragment fragment: String?) -> [String: String] {
        guard
            let fragment,
            !fragment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            return [:]
        }

        let fragmentURL = URL(string: "https://fragment.local?\(fragment)")
        let fragmentItems = URLComponents(
            url: fragmentURL ?? URL(string: "https://fragment.local")!,
            resolvingAgainstBaseURL: false
        )?.queryItems ?? []
        return dictionary(from: fragmentItems)
    }

    private func parseScopes(_ rawScopes: String?) -> Set<String> {
        Set(
            rawScopes
                .orEmpty
                .split(separator: " ")
                .map(String.init)
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
        )
    }
}

struct SecureAuthorizationStateGenerator: AuthorizationStateGenerating {
    func generate() throws -> AuthorizationStatePayload {
        let state = try randomURLSafeString(byteCount: 32)

        return AuthorizationStatePayload(state: state)
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
    private let deviceIdKey: String
    private let secureStore: SecureKeyValueStore

    public init(
        secureStore: SecureKeyValueStore,
        deviceIdKey: String = "yandex.oauth.device-id"
    ) {
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
}

private extension Data {
    func base64URLEncodedString() -> String {
        base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

private extension Optional where Wrapped == String {
    var orEmpty: String {
        self ?? ""
    }
}
