import Foundation

public protocol TrackStreamUrlProvider: Sendable {
    func streamURL(for trackId: TrackId) async throws -> URL
}
