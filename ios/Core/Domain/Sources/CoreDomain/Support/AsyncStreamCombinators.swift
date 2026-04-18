import Foundation

public func combineLatest<Input: Sendable, Output: Sendable>(
    _ streams: [() -> AsyncStream<Input>],
    transform: @escaping @Sendable ([Input]) -> Output
) -> AsyncStream<Output> {
    AsyncStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
        let state = CombineLatestState<Input>(count: streams.count)
        let tasks = streams.enumerated().map { index, makeStream in
            Task {
                for await value in makeStream() {
                    if let combined = await state.update(
                        index: index,
                        value: value,
                        transform: transform
                    ) {
                        continuation.yield(combined)
                    }
                }
            }
        }

        continuation.onTermination = { _ in
            tasks.forEach { $0.cancel() }
        }
    }
}

public func stream<T: Sendable>(once value: T) -> AsyncStream<T> {
    AsyncStream { continuation in
        continuation.yield(value)
        continuation.finish()
    }
}

private actor CombineLatestState<Input: Sendable> {
    private var latestValues: [Int: Input] = [:]
    private let count: Int

    init(count: Int) {
        self.count = count
    }

    func update<Output: Sendable>(
        index: Int,
        value: Input,
        transform: @escaping @Sendable ([Input]) -> Output
    ) -> Output? {
        latestValues[index] = value
        guard latestValues.count == count else {
            return nil
        }

        let orderedValues = (0..<count).compactMap { latestValues[$0] }
        guard orderedValues.count == count else {
            return nil
        }
        return transform(orderedValues)
    }
}
