import CoreGraphics
import SwiftUI

public struct MultiplayerShadow {
    public let opacity: Double
    public let radius: CGFloat
    public let x: CGFloat
    public let y: CGFloat

    public init(
        opacity: Double,
        radius: CGFloat,
        x: CGFloat = 0,
        y: CGFloat = 0
    ) {
        self.opacity = opacity
        self.radius = radius
        self.x = x
        self.y = y
    }

    public var color: Color {
        Color.black.opacity(opacity)
    }
}

public struct MultiplayerElevation {
    public let level0: MultiplayerShadow?
    public let level1: MultiplayerShadow
    public let level2: MultiplayerShadow
    public let level3: MultiplayerShadow

    public init(
        level0: MultiplayerShadow? = nil,
        level1: MultiplayerShadow = MultiplayerShadow(opacity: 0.12, radius: 8, y: 4),
        level2: MultiplayerShadow = MultiplayerShadow(opacity: 0.18, radius: 18, y: 10),
        level3: MultiplayerShadow = MultiplayerShadow(opacity: 0.22, radius: 28, y: 16)
    ) {
        self.level0 = level0
        self.level1 = level1
        self.level2 = level2
        self.level3 = level3
    }
}
