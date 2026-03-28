import CoreUI
import Foundation

enum TrackListPreviewFactory {
    static func makeState(title: String = "Моя фонотека") -> TrackListState {
        TrackListState(
            title: title,
            subtitle: "4 трека • 24 ч 12 м",
            nowPlaying: NowPlayingStripState(
                title: "Midnight Drive",
                subtitle: "The Northern Lights",
                isPlaying: true,
                currentPositionSeconds: 130,
                durationSeconds: 251,
                progressFraction: 130 / 251,
                displayedProgressFraction: 130 / 251
            ),
            tracks: [
                MultiplayerTrackListItemState(
                    title: "Midnight Drive",
                    artist: "The Northern Lights",
                    duration: "4:11",
                    trackPosition: 1,
                    isActive: true
                ),
                MultiplayerTrackListItemState(
                    title: "Neon Skyline",
                    artist: "Tokyo After Dark",
                    duration: "4:11",
                    trackPosition: 2
                ),
                MultiplayerTrackListItemState(
                    title: "City Pulse",
                    artist: "Mira Lane",
                    duration: "3:58",
                    trackPosition: 3
                ),
                MultiplayerTrackListItemState(
                    title: "Slow Motion",
                    artist: "Polar Kids",
                    duration: "5:02",
                    trackPosition: 4
                )
            ]
        )
    }
}
