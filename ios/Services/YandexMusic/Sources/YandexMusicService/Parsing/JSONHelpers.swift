import CoreDomain
import Foundation

// MARK: - JSON object accessors

extension Dictionary where Key == String, Value == Any {

    func requiredObject(_ key: String) throws -> [String: Any] {
        guard let value = self[key] as? [String: Any] else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex Music response does not contain object '\(key)'."
            )
        }
        return value
    }

    func optionalObject(_ key: String) -> [String: Any]? {
        self[key] as? [String: Any]
    }

    func optionalObjectArray(_ key: String) -> [[String: Any]]? {
        self[key] as? [[String: Any]]
    }

    func optionalAnyArray(_ key: String) -> [Any]? {
        self[key] as? [Any]
    }

    func requiredString(_ key: String) throws -> String {
        guard let value = optionalString(key) else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex Music response does not contain string '\(key)'."
            )
        }
        return value
    }

    func optionalString(_ key: String) -> String? {
        self[key] as? String
    }

    func requiredStringOrInt(_ key: String) throws -> String {
        guard let value = optionalStringOrInt(key) else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex Music response does not contain identifier '\(key)'."
            )
        }
        return value
    }

    func optionalStringOrInt(_ key: String) -> String? {
        if let s = optionalString(key) { return s }
        if let n = optionalInt64(key) { return String(n) }
        return nil
    }

    func requiredInt64(_ key: String) throws -> Int64 {
        guard let value = optionalInt64(key) else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex Music response does not contain number '\(key)'."
            )
        }
        return value
    }

    func optionalInt64(_ key: String) -> Int64? {
        switch self[key] {
        case let n as Int: return Int64(n)
        case let n as Int64: return n
        case let n as NSNumber: return n.int64Value
        default: return nil
        }
    }

    func requiredBool(_ key: String) throws -> Bool {
        guard let value = optionalBool(key) else {
            throw MusicLibraryError.invalidResponse(
                description: "Yandex Music response does not contain boolean '\(key)'."
            )
        }
        return value
    }

    func optionalBool(_ key: String) -> Bool? {
        self[key] as? Bool
    }
}
