import AuthFeature
import CoreDomain
import CryptoKit
import Foundation

public final class YandexTrackStreamUrlProvider: TrackStreamUrlProvider, @unchecked Sendable {
    private let requestRunner: YandexMusicRequestRunner
    private let api: YandexMusicAPI

    public init(
        accessTokenProvider: YandexAccessTokenProvider,
        api: YandexMusicAPI
    ) {
        requestRunner = YandexMusicRequestRunner(
            accessTokenProvider: accessTokenProvider,
            api: api
        )
        self.api = api
    }

    public func streamURL(for trackId: TrackId) async throws -> URL {
        try await requestRunner.withAuthorizedRequest { accessToken in
            try await self.resolveStreamURL(accessToken: accessToken, trackId: trackId.rawValue)
        }
    }

    private func resolveStreamURL(
        accessToken: String,
        trackId: String
    ) async throws -> URL {
        if let fullTrackURL = try await resolveFullTrackURL(
            accessToken: accessToken,
            trackId: trackId
        ) {
            return fullTrackURL
        }

        let downloadInfo = try await api.fetchTrackDownloadInfo(
            accessToken: accessToken,
            trackId: trackId
        )
        guard !downloadInfo.isEmpty else {
            throw MusicLibraryError.invalidResponse(
                description: "No download info available for track \(trackId)."
            )
        }

        let bestEntry = selectBestDownloadInfo(downloadInfo)
        let downloadInfoURL = try requireString("downloadInfoUrl", in: bestEntry)
        if booleanValue("direct", in: bestEntry) {
            return try validatedURL(
                downloadInfoURL,
                description: "Direct download-info URL is invalid."
            )
        }

        let xmlResponse = try await api.fetchDownloadInfoURL(
            accessToken: accessToken,
            url: downloadInfoURL
        )
        return try buildSignedURL(xmlResponse: xmlResponse)
    }

    private func resolveFullTrackURL(
        accessToken: String,
        trackId: String
    ) async throws -> URL? {
        do {
            let fileInfo = try await api.fetchTrackFileInfo(
                accessToken: accessToken,
                trackId: trackId
            )
            guard let downloadInfo = fileInfo["downloadInfo"] as? [String: Any],
                  let rawURL = firstDownloadURL(in: downloadInfo) else {
                return nil
            }
            return try validatedURL(rawURL, description: "Track file-info URL is invalid.")
        } catch is MusicLibraryError {
            return nil
        }
    }
}

private func selectBestDownloadInfo(_ entries: [[String: Any]]) -> [String: Any] {
    let priorities: [([String: Any]) -> Bool] = [
        {
            !booleanValue("preview", in: $0) &&
                booleanValue("direct", in: $0) &&
                stringValue("container", in: $0) == preferredStreamingContainer &&
                stringValue("codec", in: $0) == preferredCodec
        },
        {
            !booleanValue("preview", in: $0) &&
                booleanValue("direct", in: $0) &&
                stringValue("container", in: $0) == preferredStreamingContainer
        },
        {
            !booleanValue("preview", in: $0) &&
                booleanValue("direct", in: $0) &&
                stringValue("codec", in: $0) == preferredCodec
        },
        {
            !booleanValue("preview", in: $0) &&
                booleanValue("direct", in: $0)
        },
        {
            !booleanValue("preview", in: $0) &&
                stringValue("codec", in: $0) == preferredCodec
        },
        { !booleanValue("preview", in: $0) }
    ]

    for predicate in priorities {
        if let best = selectHighestBitrate(in: entries, where: predicate) {
            return best
        }
    }

    return entries[0]
}

private func selectHighestBitrate(
    in entries: [[String: Any]],
    where predicate: ([String: Any]) -> Bool
) -> [String: Any]? {
    entries
        .filter(predicate)
        .max { left, right in
            integerValue("bitrateInKbps", in: left) < integerValue("bitrateInKbps", in: right)
        }
}

private func firstDownloadURL(in downloadInfo: [String: Any]) -> String? {
    if let url = downloadInfo["url"] as? String, !url.isEmpty {
        return url
    }
    if let urls = downloadInfo["urls"] as? [String] {
        return urls.first
    }
    return nil
}

private func requireString(
    _ key: String,
    in entry: [String: Any]
) throws -> String {
    guard let value = entry[key] as? String, !value.isEmpty else {
        throw MusicLibraryError.invalidResponse(
            description: "Missing '\(key)' in download info response."
        )
    }
    return value
}

private func stringValue(_ key: String, in entry: [String: Any]) -> String? {
    entry[key] as? String
}

private func booleanValue(_ key: String, in entry: [String: Any]) -> Bool {
    entry[key] as? Bool ?? false
}

private func integerValue(_ key: String, in entry: [String: Any]) -> Int {
    if let int = entry[key] as? Int {
        return int
    }
    if let number = entry[key] as? NSNumber {
        return number.intValue
    }
    return 0
}

private func buildSignedURL(xmlResponse: String) throws -> URL {
    let host = try extractXMLTag("host", from: xmlResponse)
    let path = try extractXMLTag("path", from: xmlResponse)
    let ts = try extractXMLTag("ts", from: xmlResponse)
    let signatureSalt = try extractXMLTag("s", from: xmlResponse)

    guard let trimmedPath = path.dropFirstIfPossible else {
        throw MusicLibraryError.invalidResponse(
            description: "download info path is malformed."
        )
    }

    let payload = "\(signingSalt)\(trimmedPath)\(signatureSalt)"
    let digest = Insecure.MD5.hash(data: Data(payload.utf8))
    let md5 = digest.map { String(format: "%02x", $0) }.joined()
    let rawURL = "https://\(host)/get-mp3/\(md5)/\(ts)\(path)"
    return try validatedURL(rawURL, description: "Signed download-info URL is invalid.")
}

private func extractXMLTag(
    _ tag: String,
    from xmlResponse: String
) throws -> String {
    let openTag = "<\(tag)>"
    let closeTag = "</\(tag)>"
    guard let startRange = xmlResponse.range(of: openTag) else {
        throw MusicLibraryError.invalidResponse(
            description: "Missing <\(tag)> in download info XML."
        )
    }
    let valueStart = startRange.upperBound
    guard let endRange = xmlResponse.range(of: closeTag, range: valueStart..<xmlResponse.endIndex) else {
        throw MusicLibraryError.invalidResponse(
            description: "Missing </\(tag)> in download info XML."
        )
    }
    return String(xmlResponse[valueStart..<endRange.lowerBound])
}

private func validatedURL(
    _ rawValue: String,
    description: String
) throws -> URL {
    guard let url = URL(string: rawValue) else {
        throw MusicLibraryError.invalidResponse(description: description)
    }
    return url
}

private extension String {
    var dropFirstIfPossible: String? {
        guard count > 1 else { return nil }
        return String(dropFirst())
    }
}

private let signingSalt = "XGRlBW9FXlekgbPrRHuSiA"
private let preferredCodec = "mp3"
private let preferredStreamingContainer = "hls"
