import CoreDomain
import Foundation

public struct CompositePlayableUrlResolver: PlayableUrlResolver, Sendable {
    private let providers: [MusicProviderId: TrackStreamUrlProvider]

    public init(providers: [MusicProviderId: TrackStreamUrlProvider]) {
        self.providers = providers
    }

    public func playableURL(for item: PlaybackQueueItemDescriptor) async throws -> URL {
        switch item.source {
        case .local(let url):
            return url

        case .remote(let provider):
            guard let provider = providers[provider] else {
                throw MusicLibraryError.providerError(
                    code: "missing-provider",
                    description: "No stream URL provider registered for \(provider)."
                )
            }
            return try await provider.streamURL(for: item.trackId)
        }
    }
}

struct ProviderPlayableUrlResolver: PlayableUrlResolver {
    let urlProvider: TrackStreamUrlProvider

    func playableURL(for item: PlaybackQueueItemDescriptor) async throws -> URL {
        switch item.source {
        case .local(let url):
            return url

        case .remote:
            return try await urlProvider.streamURL(for: item.trackId)
        }
    }
}
