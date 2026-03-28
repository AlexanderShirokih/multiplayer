@testable import YandexMusicService
import AuthFeature
import CoreDomain
import Foundation
import XCTest

final class YandexMusicRepositoryImplTests: XCTestCase {

    // MARK: - Own playlists

    func testObserveOwnPlaylistsEmitsRefreshedSummaryResponse() async throws {
        let api = FakeYandexMusicAPI(
            currentUser: ["id": "264684056"],
            ownPlaylists: [
                [
                    "owner": ["uid": 264684056, "name": "Александр Широких"],
                    "playlistUuid": "6852c997-215a-6b20-8574-762e75b7e593",
                    "available": true,
                    "uid": 264684056,
                    "kind": 1008,
                    "title": "shared",
                    "revision": 13,
                    "snapshot": 13,
                    "trackCount": 12,
                    "visibility": "public",
                    "collective": false,
                    "durationMs": 2757910,
                    "cover": [
                        "type": "mosaic",
                        "itemsUri": ["avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%"],
                    ],
                    "ogImage": "avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%",
                ],
            ]
        )
        let repository = makeRepository(api: api)

        try await repository.refreshOwnPlaylists()
        let playlists = await firstValue(repository.observeOwnPlaylists())

        XCTAssertEqual(playlists.count, 1)
        let summary = playlists[0]
        XCTAssertEqual(summary.provider, .yandexMusic)
        XCTAssertEqual(summary.id.ownerId.rawValue, "264684056")
        XCTAssertEqual(summary.id.kind.rawValue, 1008)
        XCTAssertEqual(summary.title, "shared")
        XCTAssertEqual(summary.ownerName, "Александр Широких")
        XCTAssertEqual(
            summary.coverUriTemplate,
            "avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%"
        )
        XCTAssertEqual(summary.trackCount, 12)
        XCTAssertEqual(summary.durationMs, 2_757_910)
        XCTAssertEqual(summary.visibility, .public)
    }

    // MARK: - Playlist with rich tracks

    func testObservePlaylistEmitsRefreshedRichTrackEntries() async throws {
        let playlistId = PlaylistId(
            ownerId: ProviderUserId(rawValue: "264684056"),
            kind: PlaylistKind(rawValue: 1008)
        )
        let api = FakeYandexMusicAPI(
            playlist: [
                "owner": ["uid": 264684056, "name": "Александр Широких"],
                "playlistUuid": "6852c997-215a-6b20-8574-762e75b7e593",
                "available": true,
                "uid": 264684056,
                "kind": 1008,
                "title": "shared",
                "revision": 13,
                "snapshot": 13,
                "trackCount": 1,
                "visibility": "public",
                "collective": false,
                "durationMs": 2757910,
                "likesCount": 2,
                "cover": [
                    "type": "mosaic",
                    "itemsUri": ["avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%"],
                ],
                "tracks": [
                    [
                        "id": 33207297,
                        "originalIndex": 0,
                        "originalShuffleIndex": 11,
                        "recent": false,
                        "timestamp": "2024-07-18T10:16:52+00:00",
                        "track": [
                            "id": "33207297",
                            "title": "Track title",
                            "available": true,
                            "availableForPremiumUsers": true,
                            "availableFullWithoutPermission": false,
                            "coverUri": "avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%",
                            "durationMs": 222000,
                            "lyricsAvailable": true,
                            "artists": [["id": 15, "name": "Artist"]],
                            "albums": [["id": 4056452]],
                        ] as [String: Any],
                    ] as [String: Any],
                ],
            ]
        )
        let repository = makeRepository(api: api)

        try await repository.refreshPlaylist(id: playlistId)
        let playlist = await firstValue(repository.observePlaylist(id: playlistId))

        let unwrapped = try XCTUnwrap(playlist)
        XCTAssertEqual(unwrapped.revision, 13)
        XCTAssertEqual(unwrapped.snapshot, 13)
        XCTAssertEqual(unwrapped.likesCount, 2)
        XCTAssertEqual(unwrapped.tracks.count, 1)

        let entry = unwrapped.tracks[0]
        XCTAssertEqual(entry.position, 0)
        XCTAssertEqual(entry.originalIndex, 0)
        XCTAssertEqual(entry.originalShuffleIndex, 11)
        XCTAssertEqual(entry.trackRef.trackId.rawValue, "33207297")
        XCTAssertEqual(entry.trackRef.albumId?.rawValue, "4056452")
        XCTAssertEqual(entry.track?.preview.title, "Track title")
        XCTAssertEqual(entry.track?.lyricsAvailable, true)
    }

    // MARK: - Saved tracks — private library sentinel

    func testObserveSavedTracksEmitsPrivateLibrarySentinelAfterRefresh() async throws {
        let api = FakeYandexMusicAPI(
            currentUser: ["id": "264684056"],
            savedTracks: "private-library"
        )
        let repository = makeRepository(api: api)

        try await repository.refreshSavedTracks()
        let result = await firstValue(repository.observeSavedTracks())

        if case .privateLibrary = result {
            // expected
        } else {
            XCTFail("Expected .privateLibrary, got \(result)")
        }
    }

    // MARK: - Track cache after refreshPlaylist

    func testObserveTracksEmitsCachedTracksAfterRefreshPlaylist() async throws {
        let api = FakeYandexMusicAPI(
            playlist: [
                "owner": ["uid": 264684056, "name": "Александр Широких"],
                "playlistUuid": "6852c997-215a-6b20-8574-762e75b7e593",
                "available": true,
                "uid": 264684056,
                "kind": 1008,
                "title": "shared",
                "trackCount": 1,
                "tracks": [
                    [
                        "id": 33207297,
                        "timestamp": "2024-07-18T10:16:52+00:00",
                        "track": [
                            "id": "33207297",
                            "title": "Track title",
                            "available": true,
                            "availableForPremiumUsers": true,
                            "availableFullWithoutPermission": false,
                            "coverUri": "avatars.yandex.net/get-music-content/163479/e1a3abf0.a.4056452-1/%%",
                            "durationMs": 222000,
                            "lyricsAvailable": true,
                            "artists": [["id": 15, "name": "Artist"]],
                            "albums": [["id": 4056452]],
                        ] as [String: Any],
                    ] as [String: Any],
                ],
            ]
        )
        let repository = makeRepository(api: api)

        try await repository.refreshPlaylist(
            id: PlaylistId(
                ownerId: ProviderUserId(rawValue: "264684056"),
                kind: PlaylistKind(rawValue: 1008)
            )
        )

        let refs = [TrackRef(trackId: TrackId(rawValue: "33207297"), albumId: AlbumId(rawValue: "4056452"))]
        let tracks = await firstValue(repository.observeTracks(refs: refs))

        XCTAssertEqual(tracks.count, 1)
        XCTAssertEqual(tracks[0].preview.title, "Track title")
    }

    // MARK: - Helpers

    private func makeRepository(api: YandexMusicAPI) -> YandexMusicRepositoryImpl {
        YandexMusicRepositoryImpl(
            accessTokenProvider: FakeYandexAccessTokenProvider(),
            api: api
        )
    }

    private func firstValue<T>(_ stream: AsyncStream<T>) async -> T {
        var iterator = stream.makeAsyncIterator()
        guard let value = await iterator.next() else {
            fatalError("Stream completed without emitting a value")
        }
        return value
    }
}

// MARK: - Test doubles

private final class FakeYandexMusicAPI: YandexMusicAPI, @unchecked Sendable {
    private let availabilityResponse: [String: Any]
    private let currentUserResponse: [String: Any]
    private let ownPlaylistsResponse: [[String: Any]]
    private let playlistResponse: [String: Any]
    private let savedTracksResponse: Any
    private let tracksResponse: [[String: Any]]

    init(
        availability: [String: Any] = [:],
        currentUser: [String: Any] = [:],
        ownPlaylists: [[String: Any]] = [],
        playlist: [String: Any] = [:],
        savedTracks: Any = [String: Any](),
        tracks: [[String: Any]] = []
    ) {
        availabilityResponse = availability
        currentUserResponse = currentUser
        ownPlaylistsResponse = ownPlaylists
        playlistResponse = playlist
        savedTracksResponse = savedTracks
        tracksResponse = tracks
    }

    func fetchAvailability(accessToken: String) async throws -> [String: Any] {
        availabilityResponse
    }

    func fetchCurrentUser(accessToken: String) async throws -> [String: Any] {
        currentUserResponse
    }

    func fetchOwnPlaylists(accessToken: String, userId: String) async throws -> [[String: Any]] {
        ownPlaylistsResponse
    }

    func fetchPlaylist(accessToken: String, userId: String, kind: Int64) async throws -> [String: Any] {
        playlistResponse
    }

    func fetchSavedTracks(accessToken: String, userId: String) async throws -> Any {
        savedTracksResponse
    }

    func fetchTracks(accessToken: String, trackIds: [String]) async throws -> [[String: Any]] {
        tracksResponse
    }
}

private struct FakeYandexAccessTokenProvider: YandexAccessTokenProvider {
    func validAccessToken(forceRefresh: Bool) async throws -> String {
        "test-token"
    }
}
