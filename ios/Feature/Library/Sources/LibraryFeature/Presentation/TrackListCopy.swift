import Foundation

enum TrackListCopy {
    static func trackCountSubtitle(trackCount: Int) -> String {
        "\(trackCount) \(trackWord(for: trackCount))"
    }

    static let loadingMessage = "Загружаем треки…"
    static let loadErrorMessage = "Не удалось загрузить список треков. Попробуйте ещё раз."
    static let privateLibraryMessage = "Эта медиатека недоступна для просмотра."
    static let emptyMessage = "В этом списке пока нет треков."
    static let retry = "Повторить"
}

private func trackWord(for count: Int) -> String {
    let mod10 = count % 10
    let mod100 = count % 100

    if mod10 == 1, mod100 != 11 {
        return "трек"
    }
    if (2 ... 4).contains(mod10), !(12 ... 14).contains(mod100) {
        return "трека"
    }
    return "треков"
}
