import SwiftUI

#Preview("Track list item - light") {
    MultiplayerPreview(colorScheme: .light, alignment: .topLeading) {
        MultiplayerCardSurface(style: .surface2) {
            MultiplayerTrackListItemPreviewContent()
        }
    }
}

#Preview("Track list item - dark") {
    MultiplayerPreview(colorScheme: .dark, alignment: .topLeading) {
        MultiplayerCardSurface(style: .surface2) {
            MultiplayerTrackListItemPreviewContent()
        }
    }
}

private struct MultiplayerTrackListItemPreviewContent: View {
    var body: some View {
        VStack(spacing: 0) {
            MultiplayerTrackListItem(
                state: MultiplayerTrackListItemState(
                    title: "Midnight Drive",
                    artist: "The Northern Lights",
                    duration: "4:11",
                    trackPosition: 1,
                    isActive: true
                )
            )
            MultiplayerTrackListItem(
                state: MultiplayerTrackListItemState(
                    title: "Neon Skyline",
                    artist: "Tokyo After Dark",
                    duration: "4:11",
                    trackPosition: 2,
                    isActive: false
                )
            )
            MultiplayerTrackListItem(
                state: MultiplayerTrackListItemState(
                    title: "City Pulse",
                    artist: "Mira Lane",
                    duration: "3:58",
                    trackPosition: 3,
                    isActive: false
                )
            )
            MultiplayerTrackListItem(
                state: MultiplayerTrackListItemState(
                    title: "Slow Motion",
                    artist: "Polar Kids",
                    duration: "5:02",
                    trackPosition: 4,
                    isActive: false
                )
            )
        }
    }
}
