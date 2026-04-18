import Foundation
import MediaPlayer
import UIKit

public protocol ArtworkLoading: Sendable {
    func loadArtwork(for artworkUri: String?) async -> MPMediaItemArtwork?
}

public final class ArtworkLoader: ArtworkLoading, @unchecked Sendable {
    private let session: URLSession
    private let cache = NSCache<NSURL, MPMediaItemArtwork>()

    public init(session: URLSession = .shared) {
        self.session = session
    }

    public func loadArtwork(for artworkUri: String?) async -> MPMediaItemArtwork? {
        guard let resolvedURL = resolvedArtworkURL(from: artworkUri) else {
            return nil
        }

        let cacheKey = resolvedURL as NSURL
        if let cachedArtwork = cache.object(forKey: cacheKey) {
            return cachedArtwork
        }

        do {
            let (data, _) = try await session.data(from: resolvedURL)
            guard let image = UIImage(data: data) else {
                return nil
            }
            let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
            cache.setObject(artwork, forKey: cacheKey)
            return artwork
        } catch {
            return nil
        }
    }

    private func resolvedArtworkURL(from artworkUri: String?) -> URL? {
        guard let artworkUri else {
            return nil
        }
        let normalized = artworkUri.replacingOccurrences(of: "%%", with: "400x400")
        return URL(string: normalized)
    }
}
