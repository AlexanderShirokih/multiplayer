import CoreDomain
import Foundation

struct DeviceMediaItemRecord: Sendable, Equatable {
    let persistentID: UInt64
    let title: String
    let artist: String
    let durationMs: Int64
    let assetURL: URL
}

struct DeviceMappedTrack: Sendable {
    let assetURL: URL
    let entry: PlaylistTrackEntry
}

enum DeviceMediaItemMapper {
    static func map(record: DeviceMediaItemRecord, position: Int) -> DeviceMappedTrack? {
        let title = record.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let artist = record.artist.trimmingCharacters(in: .whitespacesAndNewlines)
        guard shouldInclude(title: title, durationMs: record.durationMs) else {
            return nil
        }

        let trackId = TrackId(rawValue: "device:\(record.persistentID)")
        return DeviceTrackBuilder.build(
            DeviceTrackBuilder.Input(
                assetURL: record.assetURL,
                trackId: trackId,
                artistKey: "device:\(artist.isEmpty ? "unknown" : artist)",
                title: title,
                artist: artist,
                durationMs: record.durationMs
            ),
            position: position
        )
    }

    static func map(_ records: [DeviceMediaItemRecord]) -> [DeviceMappedTrack] {
        var position = 0
        return records.compactMap { record in
            defer {
                if shouldInclude(
                    title: record.title.trimmingCharacters(in: .whitespacesAndNewlines),
                    durationMs: record.durationMs
                ) {
                    position += 1
                }
            }
            return map(record: record, position: position)
        }
    }

    private static func shouldInclude(title: String, durationMs: Int64) -> Bool {
        !title.isEmpty && durationMs > 0
    }
}

enum DeviceTrackBuilder {
    static let unknownArtistName = "Неизвестный исполнитель"

    struct Input {
        let assetURL: URL
        let trackId: TrackId
        let artistKey: String
        let title: String
        let artist: String
        let durationMs: Int64?
    }

    static func build(_ input: Input, position: Int) -> DeviceMappedTrack {
        let trackRef = TrackRef(trackId: input.trackId, albumId: nil)
        let displayArtist = input.artist.isEmpty ? unknownArtistName : input.artist
        return DeviceMappedTrack(
            assetURL: input.assetURL,
            entry: PlaylistTrackEntry(
                position: position,
                addedAt: nil,
                originalIndex: nil,
                originalShuffleIndex: nil,
                isRecent: nil,
                trackRef: trackRef,
                track: Track(
                    preview: TrackPreview(
                        ref: trackRef,
                        title: input.title,
                        artists: [
                            ArtistPreview(id: input.artistKey, name: displayArtist)
                        ],
                        durationMs: input.durationMs,
                        coverUriTemplate: nil,
                        isAvailable: true
                    ),
                    lyricsAvailable: false,
                    isAvailableForPremium: true,
                    isAvailableWithoutPermission: true
                )
            )
        )
    }
}
