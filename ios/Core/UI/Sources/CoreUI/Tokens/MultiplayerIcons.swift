import SwiftUI

public struct MultiplayerIcons {
    public let appLogoAssetName: String?
    public let playSymbolName: String
    public let pauseSymbolName: String
    public let nextSymbolName: String
    public let previousSymbolName: String

    public init(
        appLogoAssetName: String? = nil,
        playSymbolName: String = "play.fill",
        pauseSymbolName: String = "pause.fill",
        nextSymbolName: String = "forward.fill",
        previousSymbolName: String = "backward.fill"
    ) {
        self.appLogoAssetName = appLogoAssetName
        self.playSymbolName = playSymbolName
        self.pauseSymbolName = pauseSymbolName
        self.nextSymbolName = nextSymbolName
        self.previousSymbolName = previousSymbolName
    }

    public var play: Image {
        Image(systemName: playSymbolName)
    }

    public var pause: Image {
        Image(systemName: pauseSymbolName)
    }

    public var next: Image {
        Image(systemName: nextSymbolName)
    }

    public var previous: Image {
        Image(systemName: previousSymbolName)
    }
}
