import CorePlayer
import CoreUI
import Foundation

enum TrackListPlaybackUI {
    static func buildExternalStripState(
        from playbackState: PlaybackQueueState
    ) -> NowPlayingStripExternalState {
        let currentItem = playbackState.currentItem
        return NowPlayingStripExternalState(
            title: currentItem?.title ?? "",
            subtitle: currentItem?.subtitle ?? "",
            isPlaying: playbackState.isPlaying,
            currentPositionMs: playbackState.currentPositionMs,
            durationMs: max(currentItem?.durationMs ?? 0, 0),
            controlsEnabled: playbackState.controlsEnabled
        )
    }

    static func buildStripState(
        activeTrackIndex: Int?,
        externalStripState: NowPlayingStripExternalState,
        isSeeking: Bool,
        seekPreviewFraction: Double?
    ) -> NowPlayingStripState? {
        guard activeTrackIndex != nil else { return nil }
        let progress = progressFraction(from: externalStripState)
        let displayed = seekPreviewFraction ?? progress
        let durationMs = max(externalStripState.durationMs, 0)
        return NowPlayingStripState(
            title: externalStripState.title,
            subtitle: externalStripState.subtitle,
            isPlaying: externalStripState.isPlaying,
            currentPositionSeconds: TimeInterval(externalStripState.currentPositionMs) / 1000,
            durationSeconds: TimeInterval(durationMs) / 1000,
            progressFraction: progress,
            isSeekInProgress: isSeeking,
            displayedProgressFraction: displayed,
            controlsEnabled: externalStripState.controlsEnabled
        )
    }

    static func progressFraction(from external: NowPlayingStripExternalState) -> Double {
        let durationMs = max(external.durationMs, 0)
        guard durationMs > 0 else { return 0 }
        let positionMs = min(max(external.currentPositionMs, 0), durationMs)
        return Double(positionMs) / Double(durationMs)
    }
}
