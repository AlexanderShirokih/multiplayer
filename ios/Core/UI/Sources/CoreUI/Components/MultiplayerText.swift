import SwiftUI

public struct MultiplayerText: View {
    private let text: Text
    private let style: MultiplayerTextStyle
    private let color: Color?
    private let alignment: TextAlignment
    private let lineLimit: Int?

    public init(
        _ key: LocalizedStringKey,
        style: MultiplayerTextStyle,
        color: Color? = nil,
        alignment: TextAlignment = .leading,
        lineLimit: Int? = nil
    ) {
        self.text = Text(key)
        self.style = style
        self.color = color
        self.alignment = alignment
        self.lineLimit = lineLimit
    }

    public init(
        verbatim content: String,
        style: MultiplayerTextStyle,
        color: Color? = nil,
        alignment: TextAlignment = .leading,
        lineLimit: Int? = nil
    ) {
        self.text = Text(verbatim: content)
        self.style = style
        self.color = color
        self.alignment = alignment
        self.lineLimit = lineLimit
    }

    public var body: some View {
        text
            .multiplayerTextStyle(style)
            .foregroundStyle(color ?? .primary)
            .multilineTextAlignment(alignment)
            .lineLimit(lineLimit)
    }
}
