import CoreDomain
import Foundation

// MARK: - Account status

extension [String: Any] {
    func toAvailability() throws -> MusicServiceAvailability {
        let account = try requiredObject("account")
        let permissions = optionalObject("permissions")
        let permissionValues = permissions?
            .optionalAnyArray("values")?
            .compactMap { $0 as? String } ?? []
        return MusicServiceAvailability(
            isAvailable: try account.requiredBool("serviceAvailable"),
            region: account.optionalInt64("region").map(Int.init),
            permissions: Set(permissionValues)
        )
    }

    func toCurrentUserId() throws -> ProviderUserId {
        guard let id = optionalStringOrInt("id") ?? optionalStringOrInt("default_uid") else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex user info response does not contain user id."
            )
        }
        return ProviderUserId(rawValue: id)
    }
}

// MARK: - Playlists

extension [String: Any] {
    func toPlaylistSummary() throws -> PlaylistSummary {
        let owner = try requiredObject("owner")
        let ownerUid = owner.optionalInt64("uid").map(String.init)
        let ownerId = try ownerUid ?? String(requiredInt64("uid"))
        return PlaylistSummary(
            id: PlaylistId(
                ownerId: ProviderUserId(rawValue: ownerId),
                kind: PlaylistKind(rawValue: try requiredInt64("kind"))
            ),
            provider: .yandexMusic,
            playlistUuid: optionalString("playlistUuid").map(PlaylistUuid.init(rawValue:)),
            title: try requiredString("title"),
            ownerName: owner.optionalString("name"),
            coverUriTemplate: coverUriTemplate(),
            trackCount: Int(try requiredInt64("trackCount")),
            durationMs: optionalInt64("durationMs"),
            isAvailable: optionalBool("available") ?? true,
            isCollective: optionalBool("collective") ?? false,
            visibility: optionalString("visibility").flatMap(\.toPlaylistVisibility),
            role: playlistRole()
        )
    }

    func toPlaylist() throws -> Playlist {
        let trackItems = optionalObjectArray("tracks") ?? []
        let tracks = try trackItems.enumerated().map { index, element in
            try element.toPlaylistTrackEntry(position: index)
        }
        return Playlist(
            summary: try toPlaylistSummary(),
            revision: optionalInt64("revision"),
            snapshot: optionalInt64("snapshot"),
            likesCount: optionalInt64("likesCount").map(Int.init),
            tracks: tracks
        )
    }

    private func toPlaylistTrackEntry(position: Int) throws -> PlaylistTrackEntry {
        let track = try optionalObject("track")?.toTrack()
        let trackId = optionalInt64("id").map(String.init)
            ?? track?.preview.ref.trackId.rawValue
        guard let trackId else {
            throw MusicLibraryError.invalidResponse(
                description: "Playlist track entry does not contain track id."
            )
        }
        return PlaylistTrackEntry(
            position: position,
            addedAt: optionalString("timestamp"),
            originalIndex: optionalInt64("originalIndex").map(Int.init),
            originalShuffleIndex: optionalInt64("originalShuffleIndex").map(Int.init),
            isRecent: optionalBool("recent"),
            trackRef: TrackRef(
                trackId: TrackId(rawValue: trackId),
                albumId: track?.preview.ref.albumId
            ),
            track: track
        )
    }
}

private let yandexFavouritesPlaylistKind: Int64 = 3

// MARK: - Saved tracks

func parseSavedTracksResult(from value: Any, userId: ProviderUserId) throws -> SavedTracksResult {
    if let sentinel = value as? String {
        if sentinel == "private-library" {
            return .privateLibrary
        }
        throw MusicLibraryError.invalidResponse(
            description: "Unexpected saved tracks sentinel '\(sentinel)'."
        )
    }

    guard let root = value as? [String: Any] else {
        throw MusicLibraryError.invalidResponse(
            description: "Saved tracks result is not a JSON object or string."
        )
    }

    // likes/tracks wraps the list in a "library" key
    let library = root.optionalObject("library") ?? root
    return try .available(library.toSavedTracks())
}

private extension [String: Any] {
    func playlistRole() -> PlaylistRole {
        let kind = optionalInt64("kind")
        return kind == yandexFavouritesPlaylistKind ? .favourites : .regular
    }

    func toSavedTracks() throws -> SavedTracks {
        guard let ownerId = optionalInt64("uid").map(String.init) else {
            throw MusicLibraryError.invalidResponse(
                description: "Saved tracks response does not contain uid."
            )
        }
        // API inconsistency: "revision" vs "revisions"
        let revision = optionalInt64("revision") ?? optionalInt64("revisions")
        let trackItems = optionalObjectArray("tracks") ?? []
        let tracks = try trackItems.enumerated().map { index, element in
            try element.toSavedTrackEntry(position: index)
        }
        return SavedTracks(
            ownerId: ProviderUserId(rawValue: ownerId),
            revision: revision,
            tracks: tracks
        )
    }

    func toSavedTrackEntry(position: Int) throws -> SavedTrackEntry {
        return SavedTrackEntry(
            position: position,
            addedAt: optionalString("timestamp"),
            trackRef: TrackRef(
                trackId: TrackId(rawValue: try requiredStringOrInt("id")),
                albumId: optionalStringOrInt("albumId").map(AlbumId.init(rawValue:))
            ),
            track: try optionalObject("track")?.toTrack().preview
        )
    }
}

// MARK: - Track

extension [String: Any] {
    func toTrack() throws -> Track {
        let trackId = optionalStringOrInt("id")
        guard let trackId else {
            throw MusicLibraryError.invalidResponse(
                description: "Track response does not contain id."
            )
        }

        let albumId = optionalObjectArray("albums")?
            .first?
            .optionalInt64("id")
            .map(String.init)

        let preview = TrackPreview(
            ref: TrackRef(
                trackId: TrackId(rawValue: trackId),
                albumId: albumId.map(AlbumId.init(rawValue:))
            ),
            title: try requiredString("title"),
            artists: optionalObjectArray("artists")?
                .compactMap { try? $0.toArtistPreview() } ?? [],
            durationMs: optionalInt64("durationMs"),
            coverUriTemplate: optionalString("coverUri") ?? optionalString("ogImage"),
            isAvailable: optionalBool("available") ?? true
        )

        return Track(
            preview: preview,
            lyricsAvailable: optionalBool("lyricsAvailable") ?? false,
            isAvailableForPremium: optionalBool("availableForPremiumUsers") ?? false,
            isAvailableWithoutPermission: optionalBool("availableFullWithoutPermission") ?? false
        )
    }

    private func toArtistPreview() throws -> ArtistPreview {
        guard let id = optionalStringOrInt("id"), let name = optionalString("name") else {
            throw MusicLibraryError.invalidResponse(
                description: "Artist entry is missing id or name."
            )
        }
        return ArtistPreview(id: id, name: name)
    }
}

// MARK: - TrackRef → API track id

extension TrackRef {
    func toApiTrackId() -> String {
        albumId.map { "\(trackId.rawValue):\($0.rawValue)" } ?? trackId.rawValue
    }
}

// MARK: - Cover URI

private extension [String: Any] {
    func coverUriTemplate() -> String? {
        let cover = optionalObject("cover")
        switch cover?.optionalString("type") {
        case "pic":
            return cover?.optionalString("uri") ?? optionalString("ogImage")

        case "mosaic":
            let firstItem = cover?.optionalAnyArray("itemsUri")?.first as? String
            return firstItem ?? optionalString("ogImage")

        default:
            return optionalString("ogImage")
        }
    }
}

// MARK: - Playlist visibility

private extension String {
    var toPlaylistVisibility: PlaylistVisibility? {
        switch self {
        case "public": return .public
        case "private": return .private
        default: return nil
        }
    }
}
