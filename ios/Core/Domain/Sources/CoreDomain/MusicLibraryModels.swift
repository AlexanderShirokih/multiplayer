import Foundation

// MARK: - Semantic identifiers

public struct ProviderUserId: RawRepresentable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }
}

public struct PlaylistKind: RawRepresentable, Hashable, Sendable {
    public let rawValue: Int64

    public init(rawValue: Int64) {
        self.rawValue = rawValue
    }
}

public struct PlaylistUuid: RawRepresentable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }
}

public struct TrackId: RawRepresentable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }
}

public struct AlbumId: RawRepresentable, Hashable, Sendable {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }
}

public struct PlaylistId: Hashable, Sendable {
    public let ownerId: ProviderUserId
    public let kind: PlaylistKind

    public init(ownerId: ProviderUserId, kind: PlaylistKind) {
        self.ownerId = ownerId
        self.kind = kind
    }
}

public struct TrackRef: Hashable, Sendable {
    public let trackId: TrackId
    public let albumId: AlbumId?

    public init(trackId: TrackId, albumId: AlbumId?) {
        self.trackId = trackId
        self.albumId = albumId
    }
}

// MARK: - Enums

public enum MusicProviderId: Sendable {
    case yandexMusic
}

public enum PlaylistVisibility: Sendable {
    case `public`
    case `private`
}

// MARK: - Service availability

public struct MusicServiceAvailability: Sendable {
    public let isAvailable: Bool
    public let region: Int?
    public let permissions: Set<String>

    public init(isAvailable: Bool, region: Int?, permissions: Set<String>) {
        self.isAvailable = isAvailable
        self.region = region
        self.permissions = permissions
    }
}

// MARK: - Playlist models

public struct PlaylistSummary: Sendable {
    public let id: PlaylistId
    public let provider: MusicProviderId
    public let playlistUuid: PlaylistUuid?
    public let title: String
    public let ownerName: String?
    public let coverUriTemplate: String?
    public let trackCount: Int
    public let durationMs: Int64?
    public let isAvailable: Bool
    public let isCollective: Bool
    public let visibility: PlaylistVisibility?

    public init(
        id: PlaylistId,
        provider: MusicProviderId,
        playlistUuid: PlaylistUuid?,
        title: String,
        ownerName: String?,
        coverUriTemplate: String?,
        trackCount: Int,
        durationMs: Int64?,
        isAvailable: Bool,
        isCollective: Bool,
        visibility: PlaylistVisibility?
    ) {
        self.id = id
        self.provider = provider
        self.playlistUuid = playlistUuid
        self.title = title
        self.ownerName = ownerName
        self.coverUriTemplate = coverUriTemplate
        self.trackCount = trackCount
        self.durationMs = durationMs
        self.isAvailable = isAvailable
        self.isCollective = isCollective
        self.visibility = visibility
    }
}

public struct Playlist: Sendable {
    public let summary: PlaylistSummary
    public let revision: Int64?
    public let snapshot: Int64?
    public let likesCount: Int?
    public let tracks: [PlaylistTrackEntry]

    public init(
        summary: PlaylistSummary,
        revision: Int64?,
        snapshot: Int64?,
        likesCount: Int?,
        tracks: [PlaylistTrackEntry]
    ) {
        self.summary = summary
        self.revision = revision
        self.snapshot = snapshot
        self.likesCount = likesCount
        self.tracks = tracks
    }
}

public struct PlaylistTrackEntry: Sendable {
    public let position: Int
    public let addedAt: String?
    public let originalIndex: Int?
    public let originalShuffleIndex: Int?
    public let isRecent: Bool?
    public let trackRef: TrackRef
    public let track: Track?

    public init(
        position: Int,
        addedAt: String?,
        originalIndex: Int?,
        originalShuffleIndex: Int?,
        isRecent: Bool?,
        trackRef: TrackRef,
        track: Track?
    ) {
        self.position = position
        self.addedAt = addedAt
        self.originalIndex = originalIndex
        self.originalShuffleIndex = originalShuffleIndex
        self.isRecent = isRecent
        self.trackRef = trackRef
        self.track = track
    }
}

// MARK: - Saved tracks

public struct SavedTracks: Sendable {
    public let ownerId: ProviderUserId
    public let revision: Int64?
    public let tracks: [SavedTrackEntry]

    public init(ownerId: ProviderUserId, revision: Int64?, tracks: [SavedTrackEntry]) {
        self.ownerId = ownerId
        self.revision = revision
        self.tracks = tracks
    }
}

public struct SavedTrackEntry: Sendable {
    public let position: Int
    public let addedAt: String?
    public let trackRef: TrackRef
    public let track: TrackPreview?

    public init(position: Int, addedAt: String?, trackRef: TrackRef, track: TrackPreview?) {
        self.position = position
        self.addedAt = addedAt
        self.trackRef = trackRef
        self.track = track
    }
}

public enum SavedTracksResult: Sendable {
    case available(SavedTracks)
    case privateLibrary
}

// MARK: - Track models

public struct TrackPreview: Sendable {
    public let ref: TrackRef
    public let title: String
    public let artists: [ArtistPreview]
    public let durationMs: Int64?
    public let coverUriTemplate: String?
    public let isAvailable: Bool

    public init(
        ref: TrackRef,
        title: String,
        artists: [ArtistPreview],
        durationMs: Int64?,
        coverUriTemplate: String?,
        isAvailable: Bool
    ) {
        self.ref = ref
        self.title = title
        self.artists = artists
        self.durationMs = durationMs
        self.coverUriTemplate = coverUriTemplate
        self.isAvailable = isAvailable
    }
}

public struct Track: Sendable {
    public let preview: TrackPreview
    public let lyricsAvailable: Bool
    public let isAvailableForPremium: Bool
    public let isAvailableWithoutPermission: Bool

    public init(
        preview: TrackPreview,
        lyricsAvailable: Bool,
        isAvailableForPremium: Bool,
        isAvailableWithoutPermission: Bool
    ) {
        self.preview = preview
        self.lyricsAvailable = lyricsAvailable
        self.isAvailableForPremium = isAvailableForPremium
        self.isAvailableWithoutPermission = isAvailableWithoutPermission
    }
}

public struct ArtistPreview: Sendable {
    public let id: String
    public let name: String

    public init(id: String, name: String) {
        self.id = id
        self.name = name
    }
}

// MARK: - Errors

public enum MusicLibraryError: Error, Equatable, Sendable {
    case unauthorized
    case serviceUnavailable(description: String?)
    case providerError(code: String, description: String?)
    case invalidResponse(description: String)
    case networkFailure(reason: String)
}
