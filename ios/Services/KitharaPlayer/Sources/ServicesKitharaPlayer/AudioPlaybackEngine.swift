import Foundation

protocol AudioPlaybackEngine: Sendable {
    var currentState: AudioEngineState { get }

    func engineStateStream() -> AsyncStream<AudioEngineState>
    func eventStream() -> AsyncStream<AudioEngineEvent>

    func play()
    func pause()
    func seekTo(positionMs: Int64) async -> Bool
    func setQueueWindow(current: AudioTrackRequest, next: AudioTrackRequest?, autoPlay: Bool)
    func appendNext(_ next: AudioTrackRequest)
    func selectInWindow(appItemId: String, autoPlay: Bool) async -> Bool
    func pruneWindow(keepAppItemIds: Set<String>)
    func stop()
}
