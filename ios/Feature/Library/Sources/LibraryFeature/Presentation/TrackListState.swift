import CoreUI
import Foundation

public struct TrackListState: Sendable {
    public let title: String
    public let subtitle: String
    public let nowPlaying: NowPlayingStripState
    public let tracks: [MultiplayerTrackListItemState]

    public init(
        title: String,
        subtitle: String,
        nowPlaying: NowPlayingStripState,
        tracks: [MultiplayerTrackListItemState]
    ) {
        self.title = title
        self.subtitle = subtitle
        self.nowPlaying = nowPlaying
        self.tracks = tracks
    }
}
