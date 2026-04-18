import Foundation

public final class AsyncValueRelay<Value: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value
    private var continuations: [UUID: AsyncStream<Value>.Continuation] = [:]

    public init(_ value: Value) {
        self.value = value
    }

    public var currentValue: Value {
        lock.withLock { value }
    }

    public func stream() -> AsyncStream<Value> {
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

    public func yield(_ newValue: Value) {
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

public final class AsyncEventRelay<Value: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var continuations: [UUID: AsyncStream<Value>.Continuation] = [:]

    public init() {}

    public func stream() -> AsyncStream<Value> {
        AsyncStream(bufferingPolicy: .bufferingNewest(16)) { continuation in
            let id = UUID()
            lock.withLock {
                continuations[id] = continuation
            }
            continuation.onTermination = { [weak self] _ in
                self?.removeContinuation(id: id)
            }
        }
    }

    public func yield(_ value: Value) {
        let activeContinuations = lock.withLock {
            Array(continuations.values)
        }
        activeContinuations.forEach { $0.yield(value) }
    }

    private func removeContinuation(id: UUID) {
        _ = lock.withLock {
            continuations.removeValue(forKey: id)
        }
    }
}

public final class AsyncEventEmitter<Event: Sendable>: @unchecked Sendable {
    private let relay = AsyncEventRelay<Event>()

    public init() {}

    public func stream() -> AsyncStream<Event> {
        relay.stream()
    }

    public func yield(_ event: Event) {
        relay.yield(event)
    }
}

extension NSLock {
    fileprivate func withLock<T>(_ operation: () -> T) -> T {
        lock()
        defer { unlock() }
        return operation()
    }
}
