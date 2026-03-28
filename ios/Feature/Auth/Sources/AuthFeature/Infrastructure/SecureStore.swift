import Foundation
import Security

public protocol SecureKeyValueStore: Sendable {
    func data(forKey key: String) throws -> Data?

    func set(_ data: Data, forKey key: String) throws

    func removeValue(forKey key: String) throws
}

public final class KeychainSecureStore: SecureKeyValueStore, @unchecked Sendable {
    private let service: String

    public init(service: String) {
        self.service = service
    }

    public func data(forKey key: String) throws -> Data? {
        let query = baseQuery(forKey: key).merging([
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]) { _, newValue in newValue }

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess:
            return item as? Data

        case errSecItemNotFound:
            return nil

        default:
            throw YandexAuthException.storageFailure(reason: "Keychain read failed with status \(status).")
        }
    }

    public func set(_ data: Data, forKey key: String) throws {
        let query = baseQuery(forKey: key)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]

        let updateStatus = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecItemNotFound {
            var insertQuery = query
            attributes.forEach { insertQuery[$0.key] = $0.value }
            let insertStatus = SecItemAdd(insertQuery as CFDictionary, nil)
            guard insertStatus == errSecSuccess else {
                throw YandexAuthException.storageFailure(
                    reason: "Keychain write failed with status \(insertStatus)."
                )
            }
            return
        }

        guard updateStatus == errSecSuccess else {
            throw YandexAuthException.storageFailure(
                reason: "Keychain update failed with status \(updateStatus)."
            )
        }
    }

    public func removeValue(forKey key: String) throws {
        let status = SecItemDelete(baseQuery(forKey: key) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw YandexAuthException.storageFailure(
                reason: "Keychain delete failed with status \(status)."
            )
        }
    }

    private func baseQuery(forKey key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key
        ]
    }
}

public final class KeychainYandexSessionStore: YandexSessionStore, @unchecked Sendable {
    private let secureStore: SecureKeyValueStore
    private let key: String
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let relay: AsyncValueRelay<YandexAuthSession?>

    public var cachedSession: YandexAuthSession? {
        relay.currentValue
    }

    public init(
        secureStore: SecureKeyValueStore,
        key: String = "yandex.oauth.session"
    ) {
        self.secureStore = secureStore
        self.key = key
        let initialSession: YandexAuthSession?
        do {
            if let data = try secureStore.data(forKey: key) {
                initialSession = try decoder.decode(YandexAuthSession.self, from: data)
            } else {
                initialSession = nil
            }
        } catch {
            initialSession = nil
        }
        relay = AsyncValueRelay(initialSession)
    }

    public func observeSession() -> AsyncStream<YandexAuthSession?> {
        relay.stream()
    }

    public func save(_ session: YandexAuthSession) async throws {
        let data = try encoder.encode(session)
        try secureStore.set(data, forKey: key)
        relay.yield(session)
    }

    public func clear() async throws {
        try secureStore.removeValue(forKey: key)
        relay.yield(nil)
    }
}

final class KeychainYandexPendingAuthorizationStore: YandexPendingAuthorizationStore, @unchecked Sendable {
    private let secureStore: SecureKeyValueStore
    private let key: String
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(
        secureStore: SecureKeyValueStore,
        key: String = "yandex.oauth.pending"
    ) {
        self.secureStore = secureStore
        self.key = key
    }

    func get() async throws -> PendingYandexAuthorization? {
        guard let data = try secureStore.data(forKey: key) else {
            return nil
        }
        return try decoder.decode(PendingYandexAuthorization.self, from: data)
    }

    func save(_ authorization: PendingYandexAuthorization) async throws {
        let data = try encoder.encode(authorization)
        try secureStore.set(data, forKey: key)
    }

    func clear() async throws {
        try secureStore.removeValue(forKey: key)
    }
}
