import CoreDomain
@testable import DeviceMusicService
import Foundation
import XCTest

final class DocumentsAudioMapperTests: XCTestCase {
    func testMapBuildsEntryWithStableTrackId() {
        let file = DocumentsAudioFile(
            url: URL(fileURLWithPath: "/Documents/Albums/Song.mp3"),
            relativePath: "Albums/Song.mp3",
            title: "Song",
            artist: "Artist",
            durationMs: 200_000
        )

        let mapped = DocumentsAudioMapper.map(file: file, position: 5)

        XCTAssertEqual(mapped?.entry.position, 5)
        XCTAssertEqual(mapped?.entry.trackRef.trackId.rawValue, "device:doc:Albums/Song.mp3")
        XCTAssertEqual(mapped?.entry.track?.preview.title, "Song")
        XCTAssertEqual(mapped?.entry.track?.preview.artists.first?.name, "Artist")
        XCTAssertEqual(mapped?.entry.track?.preview.durationMs, 200_000)
    }

    func testMapFallsBackToUnknownArtist() {
        let file = DocumentsAudioFile(
            url: URL(fileURLWithPath: "/Documents/Song.mp3"),
            relativePath: "Song.mp3",
            title: "Song",
            artist: "",
            durationMs: 200_000
        )

        let mapped = DocumentsAudioMapper.map(file: file, position: 0)

        XCTAssertEqual(mapped?.entry.track?.preview.artists.first?.name, "Неизвестный исполнитель")
    }

    func testMapKeepsTrackEvenWithoutDuration() {
        let file = DocumentsAudioFile(
            url: URL(fileURLWithPath: "/Documents/Song.mp3"),
            relativePath: "Song.mp3",
            title: "Song",
            artist: "Artist",
            durationMs: nil
        )

        let mapped = DocumentsAudioMapper.map(file: file, position: 0)

        XCTAssertNotNil(mapped)
        XCTAssertNil(mapped?.entry.track?.preview.durationMs)
    }

    func testMapSkipsBlankTitle() {
        let file = DocumentsAudioFile(
            url: URL(fileURLWithPath: "/Documents/Song.mp3"),
            relativePath: "Song.mp3",
            title: " ",
            artist: "Artist",
            durationMs: 200_000
        )

        XCTAssertNil(DocumentsAudioMapper.map(file: file, position: 0))
    }

    func testMapManyAssignsContiguousPositionsStartingFromOffset() {
        let files = [
            DocumentsAudioFile(
                url: URL(fileURLWithPath: "/Documents/A.mp3"),
                relativePath: "A.mp3",
                title: "A",
                artist: "Artist",
                durationMs: 100_000
            ),
            DocumentsAudioFile(
                url: URL(fileURLWithPath: "/Documents/B.mp3"),
                relativePath: "B.mp3",
                title: " ",
                artist: "Artist",
                durationMs: 100_000
            ),
            DocumentsAudioFile(
                url: URL(fileURLWithPath: "/Documents/C.mp3"),
                relativePath: "C.mp3",
                title: "C",
                artist: "Artist",
                durationMs: nil
            )
        ]

        let mapped = DocumentsAudioMapper.map(files, startingAt: 10)

        XCTAssertEqual(mapped.map(\.entry.position), [10, 11])
    }
}
