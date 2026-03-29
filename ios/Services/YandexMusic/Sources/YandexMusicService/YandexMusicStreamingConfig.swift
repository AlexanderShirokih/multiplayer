import Foundation

public struct YandexMusicStreamingConfig: Sendable {
    public let downloadInfoSigningSecret: String
    public let downloadInfoClientHeader: String
    public let fileInfoSigningSecret: String
    public let fileInfoClientHeader: String

    public init(
        downloadInfoSigningSecret: String = "",
        downloadInfoClientHeader: String = "",
        fileInfoSigningSecret: String = "",
        fileInfoClientHeader: String = ""
    ) {
        self.downloadInfoSigningSecret = downloadInfoSigningSecret
        self.downloadInfoClientHeader = downloadInfoClientHeader
        self.fileInfoSigningSecret = fileInfoSigningSecret
        self.fileInfoClientHeader = fileInfoClientHeader
    }

    var isDownloadInfoEnabled: Bool {
        !downloadInfoSigningSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var isFileInfoEnabled: Bool {
        !fileInfoSigningSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
