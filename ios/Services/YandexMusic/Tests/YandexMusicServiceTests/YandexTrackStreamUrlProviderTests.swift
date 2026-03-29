import AuthFeature
import CoreDomain
import Foundation
import XCTest
@testable import YandexMusicService

final class YandexTrackStreamUrlProviderTests: XCTestCase {
    func testStreamURLPrefersFullTrackFileInfoOverDownloadInfo() async throws {
        let api = FakeTrackStreamURLAPI(
            trackFileInfo: [
                "downloadInfo": [
                    "trackId": "565378",
                    "quality": "lossless",
                    "codec": "mp3",
                    "bitrate": 320,
                    "transport": "raw",
                    "url": "https://strm.yandex.ru/music-v2/raw/full-track.mp3"
                ]
            ],
            trackDownloadInfo: [
                [
                    "codec": "mp3",
                    "bitrateInKbps": 320,
                    "preview": false,
                    "downloadInfoUrl": "https://strm.yandex.ru/music/music-strm-jsons/565378/master.m3u8",
                    "direct": true,
                    "container": "hls"
                ]
            ]
        )

        let provider = YandexTrackStreamUrlProvider(
            accessTokenProvider: FakeTrackStreamAccessTokenProvider(),
            api: api
        )
        let streamURL = try await provider.streamURL(
            for: TrackId(rawValue: "565378")
        )

        XCTAssertEqual(
            streamURL.absoluteString,
            "https://strm.yandex.ru/music-v2/raw/full-track.mp3"
        )
        XCTAssertTrue(api.downloadInfoURLRequests.isEmpty)
    }

    func testStreamURLBuildsSignedURLFromLegacyDownloadInfoXML() async throws {
        let api = FakeTrackStreamURLAPI(
            trackFileInfoError: .providerError(
                code: "missing",
                description: nil
            ),
            trackDownloadInfo: [
                [
                    "codec": "mp3",
                    "bitrateInKbps": 128,
                    "preview": false,
                    "downloadInfoUrl": "https://storage.mds.yandex.net/file-download-info/id/full?sign=test",
                    "direct": false
                ]
            ],
            downloadInfoURLResponse: """
            <download-info>
              <host>example.storage.yandex.net</host>
              <path>/abc/track.mp3</path>
              <ts>12345</ts>
              <s>salt</s>
            </download-info>
            """
        )

        let provider = YandexTrackStreamUrlProvider(
            accessTokenProvider: FakeTrackStreamAccessTokenProvider(),
            api: api
        )
        let streamURL = try await provider.streamURL(
            for: TrackId(rawValue: "42")
        )

        XCTAssertEqual(
            streamURL.absoluteString,
            "https://example.storage.yandex.net/get-mp3/8fbc459932a68898f42f84a5390aa7f2/12345/abc/track.mp3"
        )
        XCTAssertEqual(
            api.downloadInfoURLRequests,
            ["https://storage.mds.yandex.net/file-download-info/id/full?sign=test"]
        )
    }
}

private final class FakeTrackStreamURLAPI: YandexMusicAPI, @unchecked Sendable {
    private let trackFileInfo: [String: Any]?
    private let trackFileInfoError: MusicLibraryError?
    private let trackDownloadInfo: [[String: Any]]
    private let downloadInfoURLResponse: String

    private(set) var downloadInfoURLRequests: [String] = []

    init(
        trackFileInfo: [String: Any]? = nil,
        trackFileInfoError: MusicLibraryError? = nil,
        trackDownloadInfo: [[String: Any]],
        downloadInfoURLResponse: String = ""
    ) {
        self.trackFileInfo = trackFileInfo
        self.trackFileInfoError = trackFileInfoError
        self.trackDownloadInfo = trackDownloadInfo
        self.downloadInfoURLResponse = downloadInfoURLResponse
    }

    func fetchAvailability(accessToken: String) async throws -> [String: Any] {
        [:]
    }

    func fetchCurrentUser(accessToken: String) async throws -> [String: Any] {
        [:]
    }

    func fetchOwnPlaylists(
        accessToken: String,
        userId: String
    ) async throws -> [[String: Any]] {
        []
    }

    func fetchPlaylist(
        accessToken: String,
        userId: String,
        kind: Int64
    ) async throws -> [String: Any] {
        [:]
    }

    func fetchSavedTracks(
        accessToken: String,
        userId: String
    ) async throws -> Any {
        [:]
    }

    func fetchTracks(
        accessToken: String,
        trackIds: [String]
    ) async throws -> [[String: Any]] {
        []
    }

    func fetchTrackDownloadInfo(
        accessToken: String,
        trackId: String
    ) async throws -> [[String: Any]] {
        trackDownloadInfo
    }

    func fetchTrackFileInfo(
        accessToken: String,
        trackId: String
    ) async throws -> [String: Any] {
        if let trackFileInfoError {
            throw trackFileInfoError
        }
        return trackFileInfo ?? [:]
    }

    func fetchDownloadInfoURL(
        accessToken: String,
        url: String
    ) async throws -> String {
        downloadInfoURLRequests.append(url)
        return downloadInfoURLResponse
    }
}

private struct FakeTrackStreamAccessTokenProvider: YandexAccessTokenProvider {
    func validAccessToken(forceRefresh: Bool) async throws -> String {
        "test-token"
    }
}
