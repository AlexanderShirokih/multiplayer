import AVFAudio
import AVFoundation
import Foundation
import os

struct DocumentsAudioFile: Sendable, Equatable {
    let url: URL
    let relativePath: String
    let title: String
    let artist: String
    let durationMs: Int64?
}

protocol DocumentsAudioLibrary: Sendable {
    func fetchAudioFiles() async -> [DocumentsAudioFile]
    func changeStream() -> AsyncStream<Void>
}

final class SystemDocumentsAudioLibrary: DocumentsAudioLibrary, @unchecked Sendable {
    private static let supportedExtensions: Set<String> = [
        "mp3", "m4a", "m4b", "mp4", "aac", "wav", "wave",
        "aif", "aifc", "aiff", "caf", "flac"
    ]
    private static let logger = Logger(
        subsystem: "com.mplayeraudio.devicemusic",
        category: "documents"
    )

    private let rootURL: URL
    private let fileManager: FileManager

    init(rootURL: URL? = nil, fileManager: FileManager = .default) {
        self.fileManager = fileManager
        self.rootURL = rootURL ?? Self.defaultDocumentsURL(fileManager: fileManager)
    }

    func fetchAudioFiles() async -> [DocumentsAudioFile] {
        let urls = enumerateAudioURLs()
        Self.logger.notice(
            "DocumentsAudio: found \(urls.count, privacy: .public) candidate files in Documents"
        )
        guard !urls.isEmpty else { return [] }

        var result: [DocumentsAudioFile] = []
        result.reserveCapacity(urls.count)
        for url in urls {
            let file = await loadFile(at: url)
            result.append(file)
        }
        Self.logger.notice(
            "DocumentsAudio: mapped \(result.count, privacy: .public) files"
        )
        return result.sorted {
            $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
        }
    }

    func changeStream() -> AsyncStream<Void> {
        let folder = rootURL
        return AsyncStream { continuation in
            ensureRootExists()
            let descriptor = open(folder.path, O_EVTONLY)
            guard descriptor >= 0 else {
                continuation.finish()
                return
            }

            let queue = DispatchQueue(label: "com.mplayeraudio.devicemusic.documents.watch")
            let source = DispatchSource.makeFileSystemObjectSource(
                fileDescriptor: descriptor,
                eventMask: [.write, .extend, .attrib, .rename, .delete],
                queue: queue
            )
            source.setEventHandler {
                continuation.yield(())
            }
            source.setCancelHandler {
                close(descriptor)
            }
            source.resume()

            continuation.onTermination = { _ in
                source.cancel()
            }
        }
    }

    private func enumerateAudioURLs() -> [URL] {
        ensureRootExists()
        let resourceKeys: [URLResourceKey] = [.isRegularFileKey, .isDirectoryKey]
        guard let enumerator = fileManager.enumerator(
            at: rootURL,
            includingPropertiesForKeys: resourceKeys,
            options: [.skipsHiddenFiles],
            errorHandler: { url, error in
                let path = url.path
                let description = error.localizedDescription
                Self.logger.error(
                    "Enumerator failed at \(path, privacy: .public): \(description, privacy: .public)"
                )
                return true
            }
        ) else {
            return []
        }

        var urls: [URL] = []
        for case let url as URL in enumerator {
            let values = try? url.resourceValues(forKeys: Set(resourceKeys))
            guard values?.isRegularFile == true else { continue }
            let ext = url.pathExtension.lowercased()
            guard Self.supportedExtensions.contains(ext) else {
                Self.logger.notice(
                    "DocumentsAudio: skip unsupported file \(url.lastPathComponent, privacy: .public)"
                )
                continue
            }
            urls.append(url)
        }
        return urls
    }

    private func loadFile(at url: URL) async -> DocumentsAudioFile {
        let asset = AVURLAsset(url: url)
        let metadata = await loadCommonMetadata(asset: asset)
        let durationMs = await resolveDurationMs(asset: asset, url: url)

        let titleFromTag = metadata.title?.trimmingCharacters(in: .whitespacesAndNewlines)
        let title: String
        if let tag = titleFromTag, !tag.isEmpty {
            title = tag
        } else {
            title = url.deletingPathExtension().lastPathComponent
        }
        let artist = (metadata.artist ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let relativePath = relativePathString(for: url)

        Self.logger.notice(
            """
            DocumentsAudio: loaded \(url.lastPathComponent, privacy: .public) \
            title=\(title, privacy: .public) \
            artist=\(artist.isEmpty ? "<none>" : artist, privacy: .public) \
            durationMs=\(durationMs.map(String.init) ?? "nil", privacy: .public)
            """
        )

        return DocumentsAudioFile(
            url: url,
            relativePath: relativePath,
            title: title,
            artist: artist,
            durationMs: durationMs
        )
    }

    private func resolveDurationMs(asset: AVURLAsset, url: URL) async -> Int64? {
        if let assetDuration = await loadAssetDurationMs(asset: asset) {
            return assetDuration
        }
        if let audioFileDuration = loadAudioFileDurationMs(url: url) {
            return audioFileDuration
        }
        Self.logger.error(
            "DocumentsAudio: failed to resolve duration for \(url.lastPathComponent, privacy: .public)"
        )
        return nil
    }

    private func loadAssetDurationMs(asset: AVURLAsset) async -> Int64? {
        do {
            let duration = try await asset.load(.duration)
            let seconds = CMTimeGetSeconds(duration)
            guard seconds.isFinite, !seconds.isNaN, seconds > 0 else {
                return nil
            }
            return Int64((seconds * 1000).rounded())
        } catch {
            let description = error.localizedDescription
            Self.logger.notice(
                "DocumentsAudio: AVURLAsset duration failed: \(description, privacy: .public)"
            )
            return nil
        }
    }

    private func loadAudioFileDurationMs(url: URL) -> Int64? {
        do {
            let file = try AVAudioFile(forReading: url)
            let sampleRate = file.processingFormat.sampleRate
            guard sampleRate > 0, file.length > 0 else {
                return nil
            }
            let seconds = Double(file.length) / sampleRate
            guard seconds.isFinite, seconds > 0 else { return nil }
            return Int64((seconds * 1000).rounded())
        } catch {
            let description = error.localizedDescription
            Self.logger.notice(
                "DocumentsAudio: AVAudioFile fallback failed: \(description, privacy: .public)"
            )
            return nil
        }
    }

    private func loadCommonMetadata(asset: AVURLAsset) async -> (title: String?, artist: String?) {
        do {
            let items = try await asset.load(.commonMetadata)
            let title = try await stringValue(forCommonKey: .commonKeyTitle, in: items)
            let artist = try await stringValue(forCommonKey: .commonKeyArtist, in: items)
            return (title, artist)
        } catch {
            let description = error.localizedDescription
            Self.logger.notice(
                "DocumentsAudio: common metadata failed: \(description, privacy: .public)"
            )
            return (nil, nil)
        }
    }

    private func stringValue(
        forCommonKey key: AVMetadataKey,
        in items: [AVMetadataItem]
    ) async throws -> String? {
        let filtered = AVMetadataItem.metadataItems(
            from: items,
            withKey: key,
            keySpace: .common
        )
        for item in filtered {
            if let value = try await item.load(.stringValue), !value.isEmpty {
                return value
            }
        }
        return nil
    }

    private func relativePathString(for url: URL) -> String {
        let standardizedRoot = rootURL.standardizedFileURL.path
        let standardized = url.standardizedFileURL.path
        if standardized.hasPrefix(standardizedRoot) {
            let dropped = standardized.dropFirst(standardizedRoot.count)
            return dropped.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        }
        return url.lastPathComponent
    }

    private func ensureRootExists() {
        if !fileManager.fileExists(atPath: rootURL.path) {
            try? fileManager.createDirectory(
                at: rootURL,
                withIntermediateDirectories: true
            )
        }
    }

    private static func defaultDocumentsURL(fileManager: FileManager) -> URL {
        if let url = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first {
            return url
        }
        return URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
    }
}
