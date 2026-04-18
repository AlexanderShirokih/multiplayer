import CoreDomain
@testable import DeviceMusicService
import Foundation
import XCTest

final class DeviceMediaItemMapperTests: XCTestCase {
    func testMapCreatesPlaylistTrackEntry() {
        let record = DeviceMediaItemRecord(
            persistentID: 42,
            title: "Song",
            artist: "Artist",
            durationMs: 185_000,
            assetURL: URL(fileURLWithPath: "/tmp/song.mp3")
        )

        let mapped = DeviceMediaItemMapper.map(record: record, position: 3)

        XCTAssertEqual(mapped?.entry.position, 3)
        XCTAssertEqual(mapped?.entry.trackRef.trackId.rawValue, "device:42")
        XCTAssertEqual(mapped?.entry.track?.preview.title, "Song")
        XCTAssertEqual(mapped?.entry.track?.preview.artists.first?.name, "Artist")
    }

    func testMapKeepsBlankArtistAsUnknown() {
        let record = DeviceMediaItemRecord(
            persistentID: 42,
            title: "Song",
            artist: "",
            durationMs: 185_000,
            assetURL: URL(fileURLWithPath: "/tmp/song.mp3")
        )

        let mapped = DeviceMediaItemMapper.map(record: record, position: 0)

        XCTAssertEqual(mapped?.entry.track?.preview.artists.first?.name, "Неизвестный исполнитель")
    }

    func testMapSkipsBlankTitle() {
        let record = DeviceMediaItemRecord(
            persistentID: 42,
            title: " ",
            artist: "Artist",
            durationMs: 185_000,
            assetURL: URL(fileURLWithPath: "/tmp/song.mp3")
        )

        XCTAssertNil(DeviceMediaItemMapper.map(record: record, position: 0))
    }

    func testMapSkipsNonPositiveDuration() {
        let record = DeviceMediaItemRecord(
            persistentID: 42,
            title: "Song",
            artist: "Artist",
            durationMs: 0,
            assetURL: URL(fileURLWithPath: "/tmp/song.mp3")
        )

        XCTAssertNil(DeviceMediaItemMapper.map(record: record, position: 0))
    }

    func testMapAssignsContiguousPositionsForValidRows() {
        let records = [
            DeviceMediaItemRecord(
                persistentID: 1,
                title: "Song A",
                artist: "Artist",
                durationMs: 180_000,
                assetURL: URL(fileURLWithPath: "/tmp/a.mp3")
            ),
            DeviceMediaItemRecord(
                persistentID: 2,
                title: " ",
                artist: "Artist",
                durationMs: 180_000,
                assetURL: URL(fileURLWithPath: "/tmp/b.mp3")
            ),
            DeviceMediaItemRecord(
                persistentID: 3,
                title: "Song C",
                artist: "Artist",
                durationMs: 180_000,
                assetURL: URL(fileURLWithPath: "/tmp/c.mp3")
            )
        ]

        let mapped = DeviceMediaItemMapper.map(records)

        XCTAssertEqual(mapped.map(\.entry.position), [0, 1])
    }
}
