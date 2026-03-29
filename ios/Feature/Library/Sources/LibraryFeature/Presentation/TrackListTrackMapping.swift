import CoreDomain
import CorePlayer
import Foundation

struct TrackListItemState: Sendable {
    let queueItem: PlaybackQueueItem
    let title: String
    let artist: String
    let duration: String
    let trackPosition: Int
}

extension PlaylistTrackEntry {
    func toTrackListItemState() -> TrackListItemState {
        let preview = track?.preview
        let trackId = trackRef.trackId.rawValue
        return TrackListItemState(
            queueItem: PlaybackQueueItem(
                id: trackQueueItemId(position: position, trackId: trackId),
                trackId: trackRef.trackId,
                title: preview?.title ?? "",
                subtitle: artistLine(from: preview?.artists),
                durationMs: preview?.durationMs ?? 0
            ),
            title: preview?.title ?? "",
            artist: artistLine(from: preview?.artists),
            duration: formatTrackDuration(durationMs: preview?.durationMs),
            trackPosition: position + 1
        )
    }
}

extension SavedTrackEntry {
    func toTrackListItemState() -> TrackListItemState {
        TrackListItemState(
            queueItem: PlaybackQueueItem(
                id: trackQueueItemId(position: position, trackId: trackRef.trackId.rawValue),
                trackId: trackRef.trackId,
                title: track?.title ?? "",
                subtitle: artistLine(from: track?.artists),
                durationMs: track?.durationMs ?? 0
            ),
            title: track?.title ?? "",
            artist: artistLine(from: track?.artists),
            duration: formatTrackDuration(durationMs: track?.durationMs),
            trackPosition: position + 1
        )
    }
}

func trackQueueItemId(position: Int, trackId: String) -> String {
    "\(position):\(trackId)"
}

func artistLine(from artists: [ArtistPreview]?) -> String {
    artists?.map(\.name).joined(separator: ", ") ?? ""
}

func formatTrackDuration(durationMs: Int64?) -> String {
    guard let durationMs, durationMs > 0 else { return "0:00" }
    let totalSeconds = durationMs / 1000
    let hours = Int(totalSeconds / 3600)
    let minutes = Int((totalSeconds % 3600) / 60)
    let seconds = Int(totalSeconds % 60)
    if hours > 0 {
        return String(format: "%d:%02d:%02d", hours, minutes, seconds)
    }
    return String(format: "%d:%02d", minutes, seconds)
}
