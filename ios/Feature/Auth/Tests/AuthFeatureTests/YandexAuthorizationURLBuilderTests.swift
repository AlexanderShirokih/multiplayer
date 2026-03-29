@testable import AuthFeature
import XCTest

final class YandexAuthorizationURLBuilderTests: XCTestCase {
    func testBuildAuthorizationURLUsesMusicTokenFlow() throws {
        let builder = YandexAuthorizationURLBuilder()
        let config = YandexOAuthConfig(
            clientId: YandexClientId(rawValue: "client-id"),
            authorizationRedirectURL: URL(string: "https://music.yandex.ru/")!
        )

        let url = try builder.buildAuthorizationURL(
            config: config,
            state: "state-456"
        )

        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        let queryItems = Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value) })

        XCTAssertEqual(queryItems["response_type"], "token")
        XCTAssertEqual(queryItems["client_id"], "client-id")
        XCTAssertEqual(queryItems["redirect_uri"], "https://music.yandex.ru/")
        XCTAssertEqual(queryItems["state"], "state-456")
        XCTAssertNil(queryItems["device_id"])
        XCTAssertNil(queryItems["code_challenge"])
    }
}
