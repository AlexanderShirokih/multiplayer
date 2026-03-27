import Foundation

extension NSLock {
    fileprivate func withLock<T>(_ operation: () -> T) -> T {
        lock()
        defer { unlock() }
        return operation()
    }
}

final class AsyncValueRelay<Value: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value
    private var continuations: [UUID: AsyncStream<Value>.Continuation] = [:]

    init(_ value: Value) {
        self.value = value
    }

    var currentValue: Value {
        lock.withLock { value }
    }

    func stream() -> AsyncStream<Value> {
        AsyncStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            let id = UUID()
            let currentValue = lock.withLock {
                continuations[id] = continuation
                return value
            }
            continuation.yield(currentValue)
            continuation.onTermination = { [weak self] _ in
                self?.removeContinuation(id: id)
            }
        }
    }

    func yield(_ newValue: Value) {
        let activeContinuations = lock.withLock {
            value = newValue
            return Array(continuations.values)
        }
        activeContinuations.forEach { $0.yield(newValue) }
    }

    private func removeContinuation(id: UUID) {
        _ = lock.withLock {
            continuations.removeValue(forKey: id)
        }
    }
}

final class AsyncEventEmitter<Event: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var continuations: [UUID: AsyncStream<Event>.Continuation] = [:]

    func stream() -> AsyncStream<Event> {
        AsyncStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            let id = UUID()
            lock.withLock {
                continuations[id] = continuation
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.lock.withLock {
                    self?.continuations.removeValue(forKey: id)
                }
            }
        }
    }

    func yield(_ event: Event) {
        let activeContinuations = lock.withLock { Array(continuations.values) }
        activeContinuations.forEach { $0.yield(event) }
    }
}
