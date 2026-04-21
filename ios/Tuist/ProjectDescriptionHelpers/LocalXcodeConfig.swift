import Foundation
import ProjectDescription

/// Читает локальные Xcode-настройки из `ios/App/Configs/Local.xcconfig`.
/// Используется для developer-specific параметров, которые не должны коммититься в репозиторий.
public enum LocalXcodeConfig {
    public static func optionalValue(for key: String) -> String? {
        if let fromEnv = normalized(ProcessInfo.processInfo.environment[key]) {
            return fromEnv
        }

        let repoRoot = repositoryRootURL()
        let localConfigURL = repoRoot
            .appendingPathComponent("ios/App/Configs/Local.xcconfig")

        guard let content = try? String(contentsOf: localConfigURL, encoding: .utf8) else {
            return nil
        }

        return xcconfigValue(for: key, in: content)
    }

    private static func repositoryRootURL() -> URL {
        let helpersFile = URL(fileURLWithPath: #filePath)
        return helpersFile
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
    }

    private static func normalized(_ value: String?) -> String? {
        guard let value else {
            return nil
        }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func xcconfigValue(for key: String, in content: String) -> String? {
        for rawLine in content.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            guard !line.isEmpty, !line.hasPrefix("//"), !line.hasPrefix("#") else {
                continue
            }

            let parts = line.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard parts.count == 2 else {
                continue
            }

            let parsedKey = String(parts[0]).trimmingCharacters(in: .whitespaces)
            guard parsedKey == key else {
                continue
            }

            return normalized(String(parts[1]))
        }

        return nil
    }
}
