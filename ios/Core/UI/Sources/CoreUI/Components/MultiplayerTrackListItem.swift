import SwiftUI

// MARK: - State

public struct MultiplayerTrackListItemState: Equatable, Sendable {
    public var title: String
    public var artist: String
    public var duration: String
    public var trackPosition: Int
    public var isActive: Bool

    public init(
        title: String,
        artist: String,
        duration: String,
        trackPosition: Int,
        isActive: Bool = false
    ) {
        self.title = title
        self.artist = artist
        self.duration = duration
        self.trackPosition = trackPosition
        self.isActive = isActive
    }
}

// MARK: - Constants

private let trackListItemMinHeight: CGFloat = 48
private let trackListIndexMinWidth: CGFloat = 22
private let trackListTrailingMinWidth: CGFloat = 76
private let trackListTextSpacing: CGFloat = 2
private let trackListActiveTrailingSpacing: CGFloat = 18

// MARK: - Component

public struct MultiplayerTrackListItem: View {
    @Environment(\.multiplayerTheme) private var theme

    private let state: MultiplayerTrackListItemState
    private let onClick: (() -> Void)?

    public init(
        state: MultiplayerTrackListItemState,
        onClick: (() -> Void)? = nil
    ) {
        self.state = state
        self.onClick = onClick
    }

    public var body: some View {
        HStack(spacing: theme.spacing.sm) {
            MultiplayerText(
                verbatim: "\(state.trackPosition)",
                style: theme.typography.label,
                color: theme.colors.miniPlayerSecondaryContent,
                alignment: .trailing,
                lineLimit: 1
            )
            .frame(minWidth: trackListIndexMinWidth, alignment: .trailing)

            VStack(alignment: .leading, spacing: trackListTextSpacing) {
                MultiplayerText(
                    verbatim: state.title,
                    style: theme.typography.compactTitle,
                    color: theme.colors.miniPlayerPrimaryContent,
                    lineLimit: 1
                )
                MultiplayerText(
                    verbatim: state.artist,
                    style: theme.typography.meta,
                    color: theme.colors.miniPlayerSecondaryContent,
                    lineLimit: 1
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            TrackListItemTrailing(
                state: state,
                activeColor: theme.colors.miniPlayerPrimaryContent
            )
        }
        .frame(maxWidth: .infinity, minHeight: trackListItemMinHeight)
        .contentShape(Rectangle())
        .onTapGesture {
            onClick?()
        }
    }
}

// MARK: - Trailing

private struct TrackListItemTrailing: View {
    @Environment(\.multiplayerTheme) private var theme

    let state: MultiplayerTrackListItemState
    let activeColor: Color

    var body: some View {
        HStack(spacing: trackListActiveTrailingSpacing) {
            if state.isActive {
                MultiplayerEqualizerIndicator(color: activeColor)
            }
            MultiplayerText(
                verbatim: state.duration,
                style: theme.typography.meta,
                color: activeColor,
                lineLimit: 1
            )
        }
        .frame(minWidth: trackListTrailingMinWidth, alignment: .trailing)
    }
}
