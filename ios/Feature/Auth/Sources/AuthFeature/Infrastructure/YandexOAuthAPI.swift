import Foundation

public protocol YandexOAuthAPI: Sendable {
    func exchangeAuthorizationCode(
        config: YandexOAuthConfig,
        code: String,
        codeVerifier: String,
        deviceId: String,
        deviceName: String
    ) async throws -> OAuthTokenPayload

    func refreshAccessToken(
        config: YandexOAuthConfig,
        refreshToken: YandexRefreshToken
    ) async throws -> OAuthTokenPayload

    func revokeToken(
        config: YandexOAuthConfig,
        accessToken: YandexAccessToken
    ) async throws

    func fetchUserIdentity(
        accessToken: YandexAccessToken
    ) async throws -> YandexUserIdentity
}

public final class URLSessionYandexOAuthAPI: YandexOAuthAPI, @unchecked Sendable {
    private let session: URLSession
    private let decoder: JSONDecoder

    public init(
        session: URLSession = .shared,
        decoder: JSONDecoder = JSONDecoder()
    ) {
        self.session = session
        self.decoder = decoder
    }

    public func exchangeAuthorizationCode(
        config: YandexOAuthConfig,
        code: String,
        codeVerifier: String,
        deviceId: String,
        deviceName: String
    ) async throws -> OAuthTokenPayload {
        let data = try await submitForm(
            url: URL(string: "https://oauth.yandex.ru/token")!,
            parameters: [
                "grant_type": "authorization_code",
                "code": code,
                "client_id": config.clientId.rawValue,
                "device_id": deviceId,
                "device_name": deviceName,
                "code_verifier": codeVerifier
            ],
            operation: "exchangeAuthorizationCode"
        )
        return try decodeTokenPayload(from: data)
    }

    public func refreshAccessToken(
        config: YandexOAuthConfig,
        refreshToken: YandexRefreshToken
    ) async throws -> OAuthTokenPayload {
        let data = try await submitForm(
            url: URL(string: "https://oauth.yandex.ru/token")!,
            parameters: [
                "grant_type": "refresh_token",
                "refresh_token": refreshToken.rawValue,
                "client_id": config.clientId.rawValue,
                "client_secret": config.clientSecret
            ],
            operation: "refreshAccessToken"
        )
        return try decodeTokenPayload(from: data)
    }

    public func revokeToken(
        config: YandexOAuthConfig,
        accessToken: YandexAccessToken
    ) async throws {
        _ = try await submitForm(
            url: URL(string: "https://oauth.yandex.ru/revoke_token")!,
            parameters: [
                "access_token": accessToken.rawValue,
                "client_id": config.clientId.rawValue,
                "client_secret": config.clientSecret
            ],
            operation: "revokeToken"
        )
    }

    public func fetchUserIdentity(
        accessToken: YandexAccessToken
    ) async throws -> YandexUserIdentity {
        var components = URLComponents(string: "https://login.yandex.ru/info")
        components?.queryItems = [
            URLQueryItem(name: "format", value: "json")
        ]
        guard let url = components?.url else {
            throw YandexAuthException.networkFailure(reason: "Unable to build Yandex user info URL.")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("OAuth \(accessToken.rawValue)", forHTTPHeaderField: "Authorization")

        let data = try await perform(
            request: request,
            operation: "fetchUserIdentity"
        )

        let dto: YandexUserInfoDTO
        do {
            dto = try decoder.decode(YandexUserInfoDTO.self, from: data)
        } catch {
            throw YandexAuthException.providerError(
                code: "invalid_user_info",
                description: "Yandex user info response is invalid."
            )
        }

        guard let userId = dto.id?.value ?? dto.defaultUid?.value else {
            throw YandexAuthException.providerError(
                code: "invalid_user_info",
                description: "Yandex user info response does not contain a user id."
            )
        }

        return YandexUserIdentity(
            id: YandexUserId(rawValue: userId),
            login: dto.login,
            displayName: dto.displayName,
            email: dto.defaultEmail,
            avatarId: dto.defaultAvatarId ?? dto.avatarId
        )
    }

    private func submitForm(
        url: URL,
        parameters: [String: String],
        operation: String
    ) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(
            "application/x-www-form-urlencoded; charset=utf-8",
            forHTTPHeaderField: "Content-Type"
        )
        request.httpBody = percentEncodedFormBody(parameters)
        return try await perform(request: request, operation: operation)
    }

    private func perform(
        request: URLRequest,
        operation: String
    ) async throws -> Data {
        logRequest(request, operation: operation)

        do {
            let (data, response) = try await session.data(for: request)
            try validate(response: response, data: data)
            return data
        } catch let error as YandexAuthException {
            throw error
        } catch {
            throw YandexAuthException.networkFailure(reason: error.localizedDescription)
        }
    }

    private func validate(
        response: URLResponse,
        data: Data
    ) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw YandexAuthException.networkFailure(reason: "Invalid HTTP response from Yandex OAuth.")
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            if let providerError = try? decoder.decode(YandexProviderErrorDTO.self, from: data) {
                if providerError.error == "access_denied" {
                    throw YandexAuthException.accessDenied(description: providerError.errorDescription)
                }
                throw YandexAuthException.providerError(
                    code: providerError.error ?? "unknown_error",
                    description: providerError.errorDescription
                )
            }

            throw YandexAuthException.networkFailure(
                reason: "Yandex OAuth request failed with status \(httpResponse.statusCode)."
            )
        }
    }

    private func decodeTokenPayload(from data: Data) throws -> OAuthTokenPayload {
        let dto: YandexTokenDTO
        do {
            dto = try decoder.decode(YandexTokenDTO.self, from: data)
        } catch {
            throw YandexAuthException.providerError(
                code: "invalid_token_payload",
                description: "Yandex OAuth response does not contain access_token."
            )
        }

        guard let accessToken = dto.accessToken else {
            throw YandexAuthException.providerError(
                code: "invalid_token_payload",
                description: "Yandex OAuth response does not contain access_token."
            )
        }

        let scopes = dto.scope?
            .split(separator: " ")
            .map { String($0) }
            .filter { !$0.isEmpty }

        return OAuthTokenPayload(
            tokenType: dto.tokenType ?? "bearer",
            accessToken: YandexAccessToken(rawValue: accessToken),
            refreshToken: dto.refreshToken.map { YandexRefreshToken(rawValue: $0) },
            expiresInSeconds: dto.expiresIn,
            scopes: Set(scopes ?? [])
        )
    }

    private func percentEncodedFormBody(_ parameters: [String: String]) -> Data {
        let body = parameters
            .sorted { $0.key < $1.key }
            .map { key, value in
                "\(key.urlQueryEscaped)=\(value.urlQueryEscaped)"
            }
            .joined(separator: "&")
        return Data(body.utf8)
    }

    private func logRequest(
        _ request: URLRequest,
        operation: String
    ) {
        #if DEBUG
        let method = request.httpMethod ?? "GET"
        let path = request.url?.path ?? "/"
        print("[YandexOAuth] \(operation): \(method) \(path)")
        #endif
    }
}

private struct YandexTokenDTO: Decodable {
    let accessToken: String?
    let tokenType: String?
    let refreshToken: String?
    let expiresIn: Int?
    let scope: String?

    private enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case tokenType = "token_type"
        case refreshToken = "refresh_token"
        case expiresIn = "expires_in"
        case scope
    }
}

private struct YandexProviderErrorDTO: Decodable {
    let error: String?
    let errorDescription: String?

    private enum CodingKeys: String, CodingKey {
        case error
        case errorDescription = "error_description"
    }
}

private struct YandexUserInfoDTO: Decodable {
    let id: LossyString?
    let defaultUid: LossyString?
    let login: String?
    let displayName: String?
    let defaultEmail: String?
    let defaultAvatarId: String?
    let avatarId: String?

    private enum CodingKeys: String, CodingKey {
        case id
        case defaultUid = "default_uid"
        case login
        case displayName = "display_name"
        case defaultEmail = "default_email"
        case defaultAvatarId = "default_avatar_id"
        case avatarId = "avatar_id"
    }
}

private struct LossyString: Decodable {
    let value: String

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let stringValue = try? container.decode(String.self) {
            value = stringValue
            return
        }
        if let intValue = try? container.decode(Int.self) {
            value = String(intValue)
            return
        }
        if let int64Value = try? container.decode(Int64.self) {
            value = String(int64Value)
            return
        }
        throw DecodingError.typeMismatch(
            String.self,
            DecodingError.Context(
                codingPath: decoder.codingPath,
                debugDescription: "Unsupported value for LossyString."
            )
        )
    }
}

private extension String {
    var urlQueryEscaped: String {
        var allowedCharacters = CharacterSet.urlQueryAllowed
        allowedCharacters.remove(charactersIn: "&=+")
        return addingPercentEncoding(withAllowedCharacters: allowedCharacters) ?? self
    }
}
