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
            return "\(playlist.ref.provider.renderedKey):\(playlist.ref.id.renderedKey)"
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
    public let ref: PlaylistRef
    public let title: String
    public let trackCount: Int
    public let role: PlaylistRole
    public let layout: PlaylistCardLayout
    public let style: MultiplayerCardSurfaceStyle
    public let artwork: PlaylistCardArtwork
    public let artworkSeed: Int

    public init(
        ref: PlaylistRef,
        title: String,
        trackCount: Int,
        role: PlaylistRole,
        layout: PlaylistCardLayout,
        style: MultiplayerCardSurfaceStyle,
        artwork: PlaylistCardArtwork,
        artworkSeed: Int
    ) {
        self.ref = ref
        self.title = title
        self.trackCount = trackCount
        self.role = role
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
    public let ref: PlaylistRef
    public let title: String
    public let role: PlaylistRole

    public init(ref: PlaylistRef, title: String, role: PlaylistRole) {
        self.ref = ref
        self.title = title
        self.role = role
    }

    public var id: String {
        "\(ref.provider.renderedKey):\(ref.id.renderedKey)"
    }
}

public enum MusicLibraryAction: Sendable {
    case playlistTapped(PlaylistRef)
    case dismissPlaylistDetail
}

extension PlaylistId {
    var renderedKey: String {
        "\(ownerId.rawValue):\(kind.rawValue)"
    }
}

private extension MusicProviderId {
    var renderedKey: String {
        switch self {
        case .device:
            return "device"

        case .yandexMusic:
            return "yandex"
        }
    }
}
