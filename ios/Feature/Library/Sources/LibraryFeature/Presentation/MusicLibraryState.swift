import CoreDomain
import CoreUI
import Foundation

public struct MusicLibraryState: Sendable {
    public var isLoading: Bool
    public var content: MusicLibraryContent?
    public var selectedPlaylist: MusicLibraryDestination?

    public init(
        isLoading: Bool = true,
        content: MusicLibraryContent? = nil,
        selectedPlaylist: MusicLibraryDestination? = nil
    ) {
        self.isLoading = isLoading
        self.content = content
        self.selectedPlaylist = selectedPlaylist
    }
}

public struct MusicLibraryContent: Sendable {
    public let cards: [MusicLibraryCard]

    public init(cards: [MusicLibraryCard]) {
        self.cards = cards
    }
}

public enum MusicLibraryCard: Identifiable, Hashable, Sendable {
    case playlist(PlaylistCard)

    public var id: String {
        switch self {
        case let .playlist(playlist):
            return playlist.id.renderedKey
        }
    }

    var span: LibraryGridSpan {
        switch self {
        case let .playlist(playlist):
            playlist.layout == .featured ? .fullWidth : .compact
        }
    }
}

public struct PlaylistCard: Hashable, Sendable {
    public let id: PlaylistId
    public let title: String
    public let trackCount: Int
    public let layout: PlaylistCardLayout
    public let style: MultiplayerCardSurfaceStyle
    public let artwork: PlaylistCardArtwork
    public let artworkSeed: Int

    public init(
        id: PlaylistId,
        title: String,
        trackCount: Int,
        layout: PlaylistCardLayout,
        style: MultiplayerCardSurfaceStyle,
        artwork: PlaylistCardArtwork,
        artworkSeed: Int
    ) {
        self.id = id
        self.title = title
        self.trackCount = trackCount
        self.layout = layout
        self.style = style
        self.artwork = artwork
        self.artworkSeed = artworkSeed
    }
}

public enum PlaylistCardLayout: Hashable, Sendable {
    case compact
    case featured
}

public enum PlaylistCardArtwork: Hashable, Sendable {
    case `default`
    case favourites
}

public struct MusicLibraryDestination: Hashable, Sendable, Identifiable {
    public let playlistId: PlaylistId
    public let title: String

    public init(playlistId: PlaylistId, title: String) {
        self.playlistId = playlistId
        self.title = title
    }

    public var id: String {
        playlistId.renderedKey
    }
}

public enum MusicLibraryAction: Sendable {
    case playlistTapped(PlaylistId)
    case dismissPlaylistDetail
}

extension PlaylistId {
    var renderedKey: String {
        "\(ownerId.rawValue):\(kind.rawValue)"
    }
}
