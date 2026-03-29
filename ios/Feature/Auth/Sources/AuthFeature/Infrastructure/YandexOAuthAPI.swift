import Foundation

public protocol YandexOAuthAPI: Sendable {
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

    private func logRequest(_ request: URLRequest, operation: String) {
        #if DEBUG
        let method = request.httpMethod ?? "GET"
        let path = request.url?.path ?? "/"
        print("[YandexOAuth] \(operation): \(method) \(path)")
        #endif
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

