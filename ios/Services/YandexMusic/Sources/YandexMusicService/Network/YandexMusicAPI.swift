import CoreDomain
import Foundation

// MARK: - Protocol

public protocol YandexMusicAPI: Sendable {
    func fetchAvailability(accessToken: String) async throws -> [String: Any]

    func fetchCurrentUser(accessToken: String) async throws -> [String: Any]

    func fetchOwnPlaylists(
        accessToken: String,
        userId: String
    ) async throws -> [[String: Any]]

    func fetchPlaylist(
        accessToken: String,
        userId: String,
        kind: Int64
    ) async throws -> [String: Any]

    /// Returns `[String: Any]` for a normal library object, or `String` for a sentinel value
    /// (e.g. `"private-library"`).
    func fetchSavedTracks(
        accessToken: String,
        userId: String
    ) async throws -> Any

    func fetchTracks(
        accessToken: String,
        trackIds: [String]
    ) async throws -> [[String: Any]]
}

// MARK: - URLSession implementation

public final class URLSessionYandexMusicAPI: YandexMusicAPI, @unchecked Sendable {
    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    public func fetchAvailability(accessToken: String) async throws -> [String: Any] {
        let result = try await getWrappedResult(
            url: "https://api.music.yandex.net/account/status",
            accessToken: accessToken
        )
        guard let object = result as? [String: Any] else {
            throw MusicLibraryError.invalidResponse(
                description: "account/status result is not a JSON object."
            )
        }
        return object
    }

    public func fetchCurrentUser(accessToken: String) async throws -> [String: Any] {
        var components = URLComponents(string: "https://login.yandex.ru/info")!
        components.queryItems = [URLQueryItem(name: "format", value: "json")]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        request.setValue("OAuth \(accessToken)", forHTTPHeaderField: "Authorization")
        let data = try await perform(request: request)
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw MusicLibraryError.invalidResponse(
                description: "User info response is not a JSON object."
            )
        }
        return json
    }

    public func fetchOwnPlaylists(
        accessToken: String,
        userId: String
    ) async throws -> [[String: Any]] {
        let result = try await getWrappedResult(
            url: "https://api.music.yandex.net/users/\(userId)/playlists/list",
            accessToken: accessToken
        )
        guard let array = result as? [[String: Any]] else {
            throw MusicLibraryError.invalidResponse(
                description: "playlists/list result is not a JSON array of objects."
            )
        }
        return array
    }

    public func fetchPlaylist(
        accessToken: String,
        userId: String,
        kind: Int64
    ) async throws -> [String: Any] {
        let result = try await getWrappedResult(
            url: "https://api.music.yandex.net/users/\(userId)/playlists/\(kind)",
            accessToken: accessToken
        )
        guard let object = result as? [String: Any] else {
            throw MusicLibraryError.invalidResponse(
                description: "playlist result is not a JSON object."
            )
        }
        return object
    }

    public func fetchSavedTracks(accessToken: String, userId: String) async throws -> Any {
        return try await getWrappedResult(
            url: "https://api.music.yandex.net/users/\(userId)/likes/tracks",
            accessToken: accessToken
        )
    }

    public func fetchTracks(
        accessToken: String,
        trackIds: [String]
    ) async throws -> [[String: Any]] {
        var parameters = trackIds.map { ("track-ids", $0) }
        parameters.append(("with-positions", "true"))
        let result = try await postWrappedForm(
            url: "https://api.music.yandex.net/tracks/",
            accessToken: accessToken,
            parameters: parameters
        )
        guard let array = result as? [[String: Any]] else {
            throw MusicLibraryError.invalidResponse(
                description: "tracks result is not a JSON array of objects."
            )
        }
        return array
    }

    // MARK: - Private

    private func getWrappedResult(url: String, accessToken: String) async throws -> Any {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "GET"
        request.setValue("OAuth \(accessToken)", forHTTPHeaderField: "Authorization")
        let data = try await perform(request: request)
        return try parseWrappedResult(from: data)
    }

    private func postWrappedForm(
        url: String,
        accessToken: String,
        parameters: [(String, String)]
    ) async throws -> Any {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue("OAuth \(accessToken)", forHTTPHeaderField: "Authorization")
        request.httpBody = formBody(parameters)
        let data = try await perform(request: request)
        return try parseWrappedResult(from: data)
    }

    private func parseWrappedResult(from data: Data) throws -> Any {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex Music response is not a JSON object."
            )
        }
        guard let result = json["result"] else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex Music response does not contain result."
            )
        }
        return result
    }

    private func perform(request: URLRequest) async throws -> Data {
        log(request)
        do {
            let (data, response) = try await session.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                throw MusicLibraryError.networkFailure(
                    reason: "Invalid HTTP response from Yandex Music."
                )
            }
            guard (200...299).contains(httpResponse.statusCode) else {
                throw providerError(from: data, statusCode: httpResponse.statusCode)
            }
            return data
        } catch let error as MusicLibraryError {
            throw error
        } catch {
            throw MusicLibraryError.networkFailure(reason: error.localizedDescription)
        }
    }

    private func providerError(from data: Data, statusCode: Int) -> MusicLibraryError {
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        let errorValue = json?["error"]
        var errorName: String?
        var errorMessage: String?

        if let errorObject = errorValue as? [String: Any] {
            errorName = errorObject["name"] as? String
            errorMessage = errorObject["message"] as? String
        } else if let errorString = errorValue as? String {
            errorMessage = errorString
        }

        switch statusCode {
        case 401:
            return .unauthorized
        case 451:
            return .serviceUnavailable(description: errorMessage ?? errorName)
        default:
            return .providerError(
                code: errorName ?? "http_\(statusCode)",
                description: errorMessage
            )
        }
    }

    private func formBody(_ parameters: [(String, String)]) -> Data {
        let body = parameters
            .map { key, value in "\(key.urlQueryEscaped)=\(value.urlQueryEscaped)" }
            .joined(separator: "&")
        return Data(body.utf8)
    }

    private func log(_ request: URLRequest) {
        #if DEBUG
        let method = request.httpMethod ?? "GET"
        let path = request.url?.path ?? "/"
        print("[YandexMusic] \(method) \(path)")
        #endif
    }
}

private extension String {
    var urlQueryEscaped: String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&=+")
        return addingPercentEncoding(withAllowedCharacters: allowed) ?? self
    }
}
