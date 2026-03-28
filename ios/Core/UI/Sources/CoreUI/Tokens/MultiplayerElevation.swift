import CoreGraphics
import SwiftUI

public struct MultiplayerShadow {
    public let opacity: Double
    public let radius: CGFloat
    public let offsetX: CGFloat
    public let offsetY: CGFloat

    public init(
        opacity: Double,
        radius: CGFloat,
        offsetX: CGFloat = 0,
        offsetY: CGFloat = 0
    ) {
        self.opacity = opacity
        self.radius = radius
        self.offsetX = offsetX
        self.offsetY = offsetY
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
        level1: MultiplayerShadow = MultiplayerShadow(opacity: 0.12, radius: 8, offsetY: 4),
        level2: MultiplayerShadow = MultiplayerShadow(opacity: 0.18, radius: 18, offsetY: 10),
        level3: MultiplayerShadow = MultiplayerShadow(opacity: 0.22, radius: 28, offsetY: 16)
    ) {
        self.level0 = level0
        self.level1 = level1
        self.level2 = level2
        self.level3 = level3
    }
}
