import AVFoundation
import Foundation
import MediaPlayer

protocol NowPlayingInfoCenterClient: AnyObject {
    var nowPlayingInfo: [String: Any]? { get set }
}

protocol RemoteCommandCenterClient: AnyObject {
    func setEnabled(_ isEnabled: Bool, for command: RemoteCommand)
    func setHandler(
        for command: RemoteCommand,
        handler: @escaping () -> MPRemoteCommandHandlerStatus
    )
    func setChangePlaybackPositionHandler(
        _ handler: @escaping (TimeInterval) -> MPRemoteCommandHandlerStatus
    )
    func removeHandlers()
}

protocol AudioSessionClient: AnyObject {
    func configureForPlayback() throws
    func setActive(_ isActive: Bool) throws
}

enum RemoteCommand: Hashable {
    case play
    case pause
    case togglePlayPause
    case nextTrack
    case previousTrack
    case skipForward
    case skipBackward
    case changePlaybackPosition
}

final class SystemNowPlayingInfoCenterClient: NowPlayingInfoCenterClient {
    var nowPlayingInfo: [String: Any]? {
        get { MPNowPlayingInfoCenter.default().nowPlayingInfo }
        set { MPNowPlayingInfoCenter.default().nowPlayingInfo = newValue }
    }
}

final class SystemRemoteCommandCenterClient: RemoteCommandCenterClient {
    private let commandCenter: MPRemoteCommandCenter
    private var tokens: [RemoteCommand: Any] = [:]

    init(commandCenter: MPRemoteCommandCenter = .shared()) {
        self.commandCenter = commandCenter
    }

    func setEnabled(_ isEnabled: Bool, for command: RemoteCommand) {
        commandObject(for: command)?.isEnabled = isEnabled
    }

    func setHandler(
        for command: RemoteCommand,
        handler: @escaping () -> MPRemoteCommandHandlerStatus
    ) {
        removeHandler(for: command)
        guard let commandObject = commandObject(for: command) else {
            return
        }
        tokens[command] = commandObject.addTarget { _ in
            handler()
        }
    }

    func setChangePlaybackPositionHandler(
        _ handler: @escaping (TimeInterval) -> MPRemoteCommandHandlerStatus
    ) {
        let command = commandCenter.changePlaybackPositionCommand
        removeHandler(for: .changePlaybackPosition)
        tokens[.changePlaybackPosition] = command.addTarget { event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            return handler(event.positionTime)
        }
    }

    func removeHandlers() {
        RemoteCommand.allCases.forEach(removeHandler(for:))
    }

    private func removeHandler(for command: RemoteCommand) {
        guard let token = tokens.removeValue(forKey: command),
              let commandObject = commandObject(for: command) else {
            return
        }
        commandObject.removeTarget(token)
    }

    private func commandObject(for command: RemoteCommand) -> MPRemoteCommand? {
        switch command {
        case .play:
            return commandCenter.playCommand

        case .pause:
            return commandCenter.pauseCommand

        case .togglePlayPause:
            return commandCenter.togglePlayPauseCommand

        case .nextTrack:
            return commandCenter.nextTrackCommand

        case .previousTrack:
            return commandCenter.previousTrackCommand

        case .skipForward:
            return commandCenter.skipForwardCommand

        case .skipBackward:
            return commandCenter.skipBackwardCommand

        case .changePlaybackPosition:
            return commandCenter.changePlaybackPositionCommand
        }
    }
}

extension RemoteCommand: CaseIterable {}

final class SystemAudioSessionClient: AudioSessionClient {
    private let audioSession: AVAudioSession

    init(audioSession: AVAudioSession = .sharedInstance()) {
        self.audioSession = audioSession
    }

    func configureForPlayback() throws {
        try audioSession.setCategory(
            .playback,
            mode: .default,
            policy: .longFormAudio
        )
    }

    func setActive(_ isActive: Bool) throws {
        try audioSession.setActive(isActive)
    }
}
