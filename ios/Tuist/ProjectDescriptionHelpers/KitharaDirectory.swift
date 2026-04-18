import Foundation
import ProjectDescription

/// Абсолютный путь к локальному клону Kithara для `Package.local` в Tuist.
/// Порядок: `KITHARA_DIR` в окружении → парсинг `App/Configs/Local.xcconfig` (от корня `ios/`).
public enum KitharaDirectory {
    public static func packagePath() -> Path {
        Path(stringLiteral: resolvePathString())
    }

    public static func resolvePathString() -> String {
        if let fromEnv = normalized(ProcessInfo.processInfo.environment["KITHARA_DIR"]) {
            return fromEnv
        }

        let repoRoot = repositoryRootURL()
        let localConfigURL = repoRoot
            .appendingPathComponent("ios/App/Configs/Local.xcconfig")

        guard let content = try? String(contentsOf: localConfigURL, encoding: .utf8),
              let fromFile = xcconfigValue(for: "KITHARA_DIR", in: content) else {
            fatalError(
                """
                Не задан KITHARA_DIR (путь к клону репозитория Kithara).

                Задайте в ios/App/Configs/Local.xcconfig по образцу Local.example.xcconfig:
                KITHARA_DIR = /абсолютный/путь/к/kithara

                Или экспортируйте переменную окружения перед `tuist generate`:
                export KITHARA_DIR=/путь/к/kithara

                См. BUILD.md (раздел iOS).
                """
            )
        }

        return fromFile
    }

    private static func repositoryRootURL() -> URL {
        // ios/Tuist/ProjectDescriptionHelpers/KitharaDirectory.swift → корень репозитория
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
