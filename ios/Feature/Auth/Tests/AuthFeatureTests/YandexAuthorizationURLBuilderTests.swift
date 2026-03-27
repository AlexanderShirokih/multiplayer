@testable import AuthFeature
import XCTest

final class YandexAuthorizationURLBuilderTests: XCTestCase {
    func testBuildAuthorizationURLIncludesPkceAndDeviceMetadata() throws {
        let builder = YandexAuthorizationURLBuilder()
        let config = YandexOAuthConfig(
            clientId: YandexClientId(rawValue: "client-id"),
            clientSecret: "client-secret",
            redirectURL: URL(string: "mplayeraudio://oauth/yandex")!,
            deviceName: "MultiPlayer"
        )

        let url = try builder.buildAuthorizationURL(
            config: config,
            state: "state-123",
            codeChallenge: "challenge-456",
            deviceId: YandexDeviceId(rawValue: "device-789"),
            deviceName: "iPhone 17"
        )

        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        let queryItems = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value) })

        XCTAssertEqual(queryItems["response_type"], "code")
        XCTAssertEqual(queryItems["client_id"], "client-id")
        XCTAssertEqual(queryItems["redirect_uri"], "mplayeraudio://oauth/yandex")
        XCTAssertEqual(queryItems["device_id"], "device-789")
        XCTAssertEqual(queryItems["device_name"], "iPhone 17")
        XCTAssertEqual(queryItems["state"], "state-123")
        XCTAssertEqual(queryItems["code_challenge"], "challenge-456")
        XCTAssertEqual(queryItems["code_challenge_method"], "S256")
    }
}
