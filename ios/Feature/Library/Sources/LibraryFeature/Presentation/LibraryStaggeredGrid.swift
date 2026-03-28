import SwiftUI

enum LibraryGridSpan: Sendable {
    case compact
    case fullWidth
}

private struct LibraryGridSpanKey: LayoutValueKey {
    static let defaultValue: LibraryGridSpan = .compact
}

extension View {
    func libraryGridSpan(_ span: LibraryGridSpan) -> some View {
        layoutValue(key: LibraryGridSpanKey.self, value: span)
    }
}

struct LibraryStaggeredGrid: Layout {
    let columnCount: Int
    let columnSpacing: CGFloat
    let rowSpacing: CGFloat

    init(
        columnCount: Int = 2,
        columnSpacing: CGFloat,
        rowSpacing: CGFloat
    ) {
        self.columnCount = max(columnCount, 1)
        self.columnSpacing = columnSpacing
        self.rowSpacing = rowSpacing
    }

    struct CacheData {
        var frames: [CGRect] = []
        var size: CGSize = .zero
    }

    func makeCache(subviews: Subviews) -> CacheData {
        CacheData(frames: Array(repeating: .zero, count: subviews.count))
    }

    func updateCache(_ cache: inout CacheData, subviews: Subviews) {
        cache.frames = Array(repeating: .zero, count: subviews.count)
        cache.size = .zero
    }

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout CacheData
    ) -> CGSize {
        let frames = calculateFrames(proposal: proposal, subviews: subviews)
        cache.frames = frames

        let width = proposal.width ?? frames.map(\.maxX).max() ?? 0
        let height = frames.map(\.maxY).max() ?? 0
        cache.size = CGSize(width: width, height: height)

        return cache.size
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout CacheData
    ) {
        if cache.frames.count != subviews.count {
            cache.frames = calculateFrames(proposal: proposal, subviews: subviews)
        }

        for (index, subview) in subviews.enumerated() {
            let frame = cache.frames[index]
            let origin = CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY)
            subview.place(
                at: origin,
                proposal: ProposedViewSize(width: frame.width, height: frame.height)
            )
        }
    }

    private func calculateFrames(
        proposal: ProposedViewSize,
        subviews: Subviews
    ) -> [CGRect] {
        let availableWidth = max(proposal.width ?? 0, 0)
        guard availableWidth > 0 else {
            return Array(repeating: .zero, count: subviews.count)
        }

        let totalSpacing = CGFloat(max(columnCount - 1, 0)) * columnSpacing
        let compactWidth = (availableWidth - totalSpacing) / CGFloat(columnCount)
        let compactXOffsets = (0..<columnCount).map { columnIndex in
            CGFloat(columnIndex) * (compactWidth + columnSpacing)
        }

        var columnHeights = Array(repeating: CGFloat.zero, count: columnCount)
        var frames: [CGRect] = []
        frames.reserveCapacity(subviews.count)

        for subview in subviews {
            let span = subview[LibraryGridSpanKey.self]

            switch span {
            case .fullWidth:
                let originY = columnHeights.max() ?? 0
                let size = subview.sizeThatFits(
                    ProposedViewSize(width: availableWidth, height: nil)
                )
                let frame = CGRect(origin: CGPoint(x: 0, y: originY), size: size)
                frames.append(frame)
                let nextHeight = frame.maxY + rowSpacing
                columnHeights = Array(repeating: nextHeight, count: columnCount)

            case .compact:
                let columnIndex = columnHeights.enumerated().min { lhs, rhs in
                    lhs.element < rhs.element
                }?.offset ?? 0
                let originX = compactXOffsets[columnIndex]
                let originY = columnHeights[columnIndex]
                let size = subview.sizeThatFits(
                    ProposedViewSize(width: compactWidth, height: nil)
                )
                let frame = CGRect(origin: CGPoint(x: originX, y: originY), size: size)
                frames.append(frame)
                columnHeights[columnIndex] = frame.maxY + rowSpacing
            }
        }

        return frames
    }
}
