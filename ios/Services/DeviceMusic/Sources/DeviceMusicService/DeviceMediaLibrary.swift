import Foundation
import MediaPlayer

protocol DeviceMediaLibrary: Sendable {
    func fetchAudioTracks() -> [DeviceMediaItemRecord]
    func changeStream() -> AsyncStream<Void>
}

final class SystemDeviceMediaLibrary: DeviceMediaLibrary, @unchecked Sendable {
    private let mediaLibrary = MPMediaLibrary.default()

    init() {
        mediaLibrary.beginGeneratingLibraryChangeNotifications()
    }

    deinit {
        mediaLibrary.endGeneratingLibraryChangeNotifications()
    }

    func fetchAudioTracks() -> [DeviceMediaItemRecord] {
        let query = MPMediaQuery.songs()
        let items = query.items ?? []
        return items.compactMap { item in
            guard let assetURL = item.assetURL else {
                return nil
            }
            return DeviceMediaItemRecord(
                persistentID: item.persistentID,
                title: item.title ?? "",
                artist: item.artist ?? "",
                durationMs: Int64(item.playbackDuration * 1000),
                assetURL: assetURL
            )
        }.sorted {
            $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
        }
    }

    func changeStream() -> AsyncStream<Void> {
        AsyncStream { continuation in
            let token = NotificationCenter.default.addObserver(
                forName: .MPMediaLibraryDidChange,
                object: mediaLibrary,
                queue: nil
            ) { _ in
                continuation.yield(())
            }

            continuation.onTermination = { _ in
                NotificationCenter.default.removeObserver(token)
            }
        }
    }
}
