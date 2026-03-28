import SwiftUI

#Preview("Now Playing Light Paused") {
    MultiplayerPreview(colorScheme: .light, alignment: .top) {
        NowPlayingStrip(
            state: NowPlayingStripState(
                title: "Midnight Echoes",
                subtitle: "Neon Avenue",
                currentPositionSeconds: 92,
                durationSeconds: 240,
                progressFraction: 92 / 240,
                displayedProgressFraction: 92 / 240
            ),
            onAction: { _ in }
        )
        .frame(height: stripHeight)
    }
}

#Preview("Now Playing Dark Playing") {
    MultiplayerPreview(colorScheme: .dark, alignment: .top) {
        NowPlayingStrip(
            state: NowPlayingStripState(
                title: "Midnight Echoes",
                subtitle: "Neon Avenue",
                isPlaying: true,
                currentPositionSeconds: 121,
                durationSeconds: 240,
                progressFraction: 121 / 240,
                displayedProgressFraction: 121 / 240
            ),
            onAction: { _ in }
        )
        .frame(height: stripHeight)
    }
}

#Preview("Now Playing Dark Seeking") {
    MultiplayerPreview(colorScheme: .dark, alignment: .top) {
        NowPlayingStrip(
            state: NowPlayingStripState(
                title: "Midnight Echoes",
                subtitle: "Neon Avenue",
                isPlaying: true,
                currentPositionSeconds: 121,
                durationSeconds: 240,
                progressFraction: 121 / 240,
                isSeekInProgress: true,
                displayedProgressFraction: 0.74
            ),
            onAction: { _ in }
        )
        .frame(height: stripHeight)
    }
}
