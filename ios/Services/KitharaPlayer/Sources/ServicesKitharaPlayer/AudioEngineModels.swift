import Foundation

struct AudioTrackRequest: Sendable {
    let id: String
    let url: String
}

struct AudioEngineState: Sendable {
    var status: AudioEngineStatus = .idle
    var currentPositionMs: Int64 = 0
    var durationMs: Int64?
    var currentItemId: String?
    var isPlaying = false
    var errorMessage: String?
}

enum AudioEngineStatus: Sendable {
    case idle
    case readyToPlay
    case failed
}

enum AudioEngineEvent: Sendable {
    case currentItemChanged(itemId: String?)
    case playedToEnd
    case itemFailed(itemId: String?, reason: String)
}
