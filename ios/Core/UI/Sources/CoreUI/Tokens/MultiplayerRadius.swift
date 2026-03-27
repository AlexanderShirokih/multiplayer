import CoreGraphics

public struct MultiplayerRadius {
    public let small: CGFloat
    public let medium: CGFloat
    public let large: CGFloat
    public let xLarge: CGFloat
    public let pill: CGFloat

    public init(
        small: CGFloat = 8,
        medium: CGFloat = 12,
        large: CGFloat = 20,
        xLarge: CGFloat = 28,
        pill: CGFloat = 999
    ) {
        self.small = small
        self.medium = medium
        self.large = large
        self.xLarge = xLarge
        self.pill = pill
    }
}
