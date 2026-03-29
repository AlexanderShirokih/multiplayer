import Foundation

protocol AudioPlaybackEngine: Sendable {
    var currentState: AudioEngineState { get }

    func engineStateStream() -> AsyncStream<AudioEngineState>
    func eventStream() -> AsyncStream<AudioEngineEvent>

    func play()
    func pause()
    func seekTo(positionMs: Int64) async -> Bool
    func loadTrack(_ request: AudioTrackRequest)
    func stop()
}
