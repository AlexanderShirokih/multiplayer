import CoreDomain
import CryptoKit
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

    func fetchTrackDownloadInfo(
        accessToken: String,
        trackId: String
    ) async throws -> [[String: Any]]

    func fetchTrackFileInfo(
        accessToken: String,
        trackId: String
    ) async throws -> [String: Any]

    func fetchDownloadInfoURL(
        accessToken: String,
        url: String
    ) async throws -> String
}

// MARK: - URLSession implementation

public final class URLSessionYandexMusicAPI: YandexMusicAPI, @unchecked Sendable {
    private let session: URLSession
    private let streamingConfig: YandexMusicStreamingConfig

    public init(
        session: URLSession = .shared,
        streamingConfig: YandexMusicStreamingConfig = .init()
    ) {
        self.session = session
        self.streamingConfig = streamingConfig
    }

    public func fetchAvailability(accessToken: String) async throws -> [String: Any] {
        let result = try await getWrappedResult(
            url: yandexMusicURL(path: "account/status"),
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
            url: yandexMusicURL(path: "users/\(userId)/playlists/list"),
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
            url: yandexMusicURL(path: "users/\(userId)/playlists/\(kind)"),
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
            url: yandexMusicURL(path: "users/\(userId)/likes/tracks"),
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
            url: yandexMusicURL(path: "tracks/"),
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

    public func fetchTrackDownloadInfo(
        accessToken: String,
        trackId: String
    ) async throws -> [[String: Any]] {
        let request = streamingConfig.isDownloadInfoEnabled
            ? buildStreamingDownloadInfoRequest(
                trackId: trackId,
                timestampSeconds: currentTimestampSeconds(),
                signingSecret: streamingConfig.downloadInfoSigningSecret,
                clientHeader: streamingConfig.downloadInfoClientHeader
            )
            : nil
        let result = try await getWrappedResult(
            url: yandexMusicURL(path: "tracks/\(trackId)/download-info"),
            accessToken: accessToken,
            requestParameters: request?.parameters ?? [:],
            extraHeaders: request?.headers ?? [:]
        )
        guard let array = result as? [[String: Any]] else {
            throw MusicLibraryError.invalidResponse(
                description: "download-info result is not a JSON array of objects."
            )
        }
        return array
    }

    public func fetchTrackFileInfo(
        accessToken: String,
        trackId: String
    ) async throws -> [String: Any] {
        guard streamingConfig.isFileInfoEnabled else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex full track file-info configuration is missing."
            )
        }
        let request = buildTrackFileInfoRequest(
            trackId: trackId,
            timestampSeconds: currentTimestampSeconds(),
            signingSecret: streamingConfig.fileInfoSigningSecret,
            clientHeader: streamingConfig.fileInfoClientHeader
        )
        var urlRequest = URLRequest(url: URL(string: yandexMusicURL(path: "get-file-info"))!)
        urlRequest.httpMethod = "GET"
        urlRequest.setValue("OAuth \(accessToken)", forHTTPHeaderField: "Authorization")
        request.headers.forEach { key, value in
            urlRequest.setValue(value, forHTTPHeaderField: key)
        }

        var components = URLComponents(url: urlRequest.url!, resolvingAgainstBaseURL: false)
        components?.queryItems = request.parameters.map { key, value in
            URLQueryItem(name: key, value: value)
        }
        urlRequest.url = components?.url

        let data = try await perform(request: urlRequest)
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw MusicLibraryError.invalidResponse(
                description: "get-file-info response is not a JSON object."
            )
        }
        guard let result = json["result"] as? [String: Any] else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex Music get-file-info response does not contain result."
            )
        }
        return result
    }

    public func fetchDownloadInfoURL(
        accessToken: String,
        url: String
    ) async throws -> String {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "GET"
        request.setValue("OAuth \(accessToken)", forHTTPHeaderField: "Authorization")
        let data = try await perform(request: request)
        guard let xml = String(data: data, encoding: .utf8) else {
            throw MusicLibraryError.invalidResponse(
                description: "download-info response is not valid UTF-8."
            )
        }
        return xml
    }
}

private extension URLSessionYandexMusicAPI {
    func getWrappedResult(
        url: String,
        accessToken: String,
        requestParameters: [String: String] = [:],
        extraHeaders: [String: String] = [:]
    ) async throws -> Any {
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "GET"
        request.setValue("OAuth \(accessToken)", forHTTPHeaderField: "Authorization")
        extraHeaders.forEach { key, value in
            request.setValue(value, forHTTPHeaderField: key)
        }
        if !requestParameters.isEmpty {
            var components = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)
            components?.queryItems = requestParameters.map { key, value in
                URLQueryItem(name: key, value: value)
            }
            request.url = components?.url
        }
        let data = try await perform(request: request)
        return try parseWrappedResult(from: data)
    }

    func postWrappedForm(
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

    func parseWrappedResult(from data: Data) throws -> Any {
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

    func perform(request: URLRequest) async throws -> Data {
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

    func providerError(from data: Data, statusCode: Int) -> MusicLibraryError {
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

    func formBody(_ parameters: [(String, String)]) -> Data {
        let body = parameters
            .map { key, value in "\(key.urlQueryEscaped)=\(value.urlQueryEscaped)" }
            .joined(separator: "&")
        return Data(body.utf8)
    }

    func log(_ request: URLRequest) {
        #if DEBUG
        let method = request.httpMethod ?? "GET"
        let path = request.url?.path ?? "/"
        print("[YandexMusic] \(method) \(path)")
        #endif
    }
}

private func yandexMusicURL(path: String) -> String {
    "\(yandexMusicAPIBaseURL)/\(path)"
}

private func currentTimestampSeconds() -> Int64 {
    Int64(Date().timeIntervalSince1970)
}

private let yandexMusicAPIBaseURL = "https://api.music.yandex.net"
private let yandexMusicClientHeaderName = "X-Yandex-Music-Client"
private let fileInfoQuality = "lossless"
private let fileInfoCodecs = "mp3"
private let fileInfoTransport = "raw"

private extension String {
    var urlQueryEscaped: String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&=+")
        return addingPercentEncoding(withAllowedCharacters: allowed) ?? self
    }
}

private struct SignedDownloadInfoRequest {
    let parameters: [String: String]
    let headers: [String: String]
}

private func buildStreamingDownloadInfoRequest(
    trackId: String,
    timestampSeconds: Int64,
    signingSecret: String,
    clientHeader: String
) -> SignedDownloadInfoRequest {
    let payload = "\(trackId)\(timestampSeconds)"
    let signature = hmacSHA256Base64(payload: payload, secret: signingSecret)
    return SignedDownloadInfoRequest(
        parameters: [
            "can_use_streaming": "true",
            "ts": "\(timestampSeconds)",
            "sign": signature
        ],
        headers: [yandexMusicClientHeaderName: clientHeader]
    )
}

private func buildTrackFileInfoRequest(
    trackId: String,
    timestampSeconds: Int64,
    signingSecret: String,
    clientHeader: String
) -> SignedDownloadInfoRequest {
    let payload = "\(timestampSeconds)\(trackId)\(fileInfoQuality)\(fileInfoCodecs)\(fileInfoTransport)"
    let signature = hmacSHA256Base64(payload: payload, secret: signingSecret)
        .replacingOccurrences(of: "=", with: "")
    return SignedDownloadInfoRequest(
        parameters: [
            "ts": "\(timestampSeconds)",
            "trackId": trackId,
            "quality": fileInfoQuality,
            "codecs": fileInfoCodecs,
            "transports": fileInfoTransport,
            "sign": signature
        ],
        headers: [yandexMusicClientHeaderName: clientHeader]
    )
}

private func hmacSHA256Base64(payload: String, secret: String) -> String {
    let key = SymmetricKey(data: Data(secret.utf8))
    let signature = HMAC<SHA256>.authenticationCode(
        for: Data(payload.utf8),
        using: key
    )
    return Data(signature).base64EncodedString()
}
