import CoreDomain
import Foundation

enum DocumentsAudioMapper {
    static func map(
        _ files: [DocumentsAudioFile],
        startingAt initialPosition: Int = 0
    ) -> [DeviceMappedTrack] {
        var position = initialPosition
        return files.compactMap { file in
            guard let mapped = map(file: file, position: position) else {
                return nil
            }
            position += 1
            return mapped
        }
    }

    static func map(file: DocumentsAudioFile, position: Int) -> DeviceMappedTrack? {
        let title = file.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let artist = file.artist.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty else {
            return nil
        }
        let trackId = TrackId(rawValue: "device:doc:\(file.relativePath)")
        return DeviceTrackBuilder.build(
            DeviceTrackBuilder.Input(
                assetURL: file.url,
                trackId: trackId,
                artistKey: "device:doc:artist:\(artist.isEmpty ? "unknown" : artist)",
                title: title,
                artist: artist,
                durationMs: file.durationMs
            ),
            position: position
        )
    }
}
