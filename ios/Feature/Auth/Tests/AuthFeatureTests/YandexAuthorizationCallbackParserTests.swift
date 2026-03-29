@testable import AuthFeature
import Foundation
import XCTest

final class YandexAuthorizationCallbackParserTests: XCTestCase {
    func testParseReadsAccessTokenFromFragment() throws {
        let parser = YandexAuthorizationCallbackParser()
        let callbackURL = try XCTUnwrap(
            URL(
                string: "https://music.yandex.ru/#access_token=token-123" +
                    "&token_type=bearer&expires_in=31536000" +
                    "&scope=login%3Ainfo%20music%3Acontent&state=state-42"
            )
        )

        let callback = parser.parse(callbackURL)

        XCTAssertEqual(callback.accessToken?.rawValue, "token-123")
        XCTAssertEqual(callback.tokenType, "bearer")
        XCTAssertEqual(callback.expiresInSeconds, 31_536_000)
        XCTAssertEqual(callback.scopes, ["login:info", "music:content"])
        XCTAssertEqual(callback.state, "state-42")
    }
}
