@testable import AuthFeature
import Foundation
import XCTest

final class YandexAuthRepositoryTests: XCTestCase {
    private let fixedNow = Date(timeIntervalSince1970: 1_775_012_800)

    func testCompleteAuthorizationSavesSessionAndEmitsAuthorizedState() async throws {
        let sessionStore = InMemorySessionStore()
        let secureStore = InMemorySecureStore()
        let oauthAPI = FakeYandexOAuthAPI(userIdentity: sampleUser)
        let repository = makeRepository(
            oauthAPI: oauthAPI,
            sessionStore: sessionStore,
            secureStore: secureStore
        )

        let request = try await repository.createAuthorizationRequest()
        let session = try await repository.completeAuthorization(
            callbackURL: URL(
                string: "https://music.yandex.ru/#access_token=access-token" +
                    "&token_type=bearer&expires_in=3600&scope=login%3Ainfo&state=state-123"
            )!
        )

        XCTAssertEqual(request.callbackURLPrefix, "https://music.yandex.ru/")
        XCTAssertEqual(session.accessToken.rawValue, "access-token")
        XCTAssertEqual(sessionStore.cachedSession, session)

        var iterator = repository.observeStatus().makeAsyncIterator()
        let authorizedStatus = await iterator.next()
        XCTAssertEqual(authorizedStatus, .authorized(session))
        XCTAssertEqual(session.clientId, YandexClientId(rawValue: "music-client-id"))
    }

    func testCompleteAuthorizationUsesMusicTokenFlow() async throws {
        let sessionStore = InMemorySessionStore()
        let oauthAPI = FakeYandexOAuthAPI(userIdentity: sampleUser)
        let repository = makeRepository(
            oauthAPI: oauthAPI,
            sessionStore: sessionStore,
            authorizationRedirectURL: URL(string: "https://music.yandex.ru/")!
        )

        let request = try await repository.createAuthorizationRequest()
        let session = try await repository.completeAuthorization(
            callbackURL: URL(
                string: "https://music.yandex.ru/#access_token=music-token" +
                    "&token_type=bearer&expires_in=31536000" +
                    "&scope=login%3Ainfo%20music%3Acontent&state=state-123"
            )!
        )

        XCTAssertEqual(request.callbackURLPrefix, "https://music.yandex.ru/")
        XCTAssertEqual(session.accessToken.rawValue, "music-token")
        XCTAssertEqual(session.clientId, YandexClientId(rawValue: "music-client-id"))
        XCTAssertEqual(session.scopes, ["login:info", "music:content"])
    }

    func testCompleteAuthorizationRejectsInvalidCallbackState() async throws {
        let repository = makeRepository()

        _ = try await repository.createAuthorizationRequest()

        do {
            _ = try await repository.completeAuthorization(
                callbackURL: URL(
                    string: "https://music.yandex.ru/#access_token=token-123&state=unexpected-state"
                )!
            )
            XCTFail("Expected invalid callback state error")
        } catch {
            XCTAssertEqual(error as? YandexAuthException, .invalidCallbackState)
        }
    }

    func testValidAccessTokenForceRefreshClearsSessionWhenUnsupported() async throws {
        let sessionStore = InMemorySessionStore(
            session: expiredSession(
                accessToken: "stale-token",
                refreshToken: "refresh-token"
            )
        )
        let repository = makeRepository(
            oauthAPI: FakeYandexOAuthAPI(userIdentity: sampleUser),
            sessionStore: sessionStore
        )

        do {
            _ = try await repository.validAccessToken(forceRefresh: true)
            XCTFail("Expected refresh unsupported error")
        } catch let error as YandexAuthException {
            XCTAssertEqual(
                error,
                .refreshFailed(reason: "Повторная авторизация требуется заново. Обновление токена не поддерживается.")
            )
        }

        XCTAssertNil(sessionStore.cachedSession)
    }

    func testCancelAuthorizationClearsPendingStateAndResetsStatus() async throws {
        let repository = makeRepository()

        _ = try await repository.createAuthorizationRequest()
        await repository.cancelAuthorization()

        var iterator = repository.observeStatus().makeAsyncIterator()
        let currentStatus = await iterator.next()
        XCTAssertEqual(currentStatus, .unauthorized)

        do {
            _ = try await repository.completeAuthorization(
                callbackURL: URL(
                    string: "https://music.yandex.ru/#access_token=token-123&state=state-123"
                )!
            )
            XCTFail("Expected missing pending authorization after cancellation")
        } catch {
            XCTAssertEqual(error as? YandexAuthException, .missingPendingAuthorization)
        }
    }

    private func makeRepository(
        oauthAPI: FakeYandexOAuthAPI = FakeYandexOAuthAPI(userIdentity: sampleUser),
        sessionStore: InMemorySessionStore = InMemorySessionStore(),
        secureStore: InMemorySecureStore = InMemorySecureStore(),
        authorizationRedirectURL: URL? = nil
    ) -> YandexAuthRepositoryImpl {
        let config = YandexOAuthConfig(
            clientId: YandexClientId(rawValue: "music-client-id"),
            authorizationRedirectURL: authorizationRedirectURL ?? URL(string: "https://music.yandex.ru/")!
        )
        let fixedNow = self.fixedNow

        return YandexAuthRepositoryImpl(
            config: config,
            oauthAPI: oauthAPI,
            sessionStore: sessionStore,
            pendingAuthorizationStore: KeychainYandexPendingAuthorizationStore(secureStore: secureStore),
            deviceMetadataProvider: FakeDeviceMetadataProvider(secureStore: secureStore),
            stateGenerator: FakeAuthorizationStateGenerator(),
            authorizationURLBuilder: YandexAuthorizationURLBuilder(),
            callbackParser: YandexAuthorizationCallbackParser(),
            now: { fixedNow }
        )
    }

    private func expiredSession(
        accessToken: String,
        refreshToken: String
    ) -> YandexAuthSession {
        YandexAuthSession(
            accessToken: YandexAccessToken(rawValue: accessToken),
            refreshToken: YandexRefreshToken(rawValue: refreshToken),
            tokenType: "bearer",
            expiresAt: fixedNow.addingTimeInterval(-120),
            scopes: ["login:info"],
            deviceId: YandexDeviceId(rawValue: "device-123"),
            user: sampleUser,
            clientId: YandexClientId(rawValue: "music-client-id")
        )
    }
}

private let sampleUser = YandexUserIdentity(
    id: YandexUserId(rawValue: "42"),
    login: "music-user",
    displayName: "Music User",
    email: "music@example.com",
    avatarId: "avatar-id"
)

private final class InMemorySessionStore: YandexSessionStore {
    private let relay: AsyncValueRelay<YandexAuthSession?>

    var cachedSession: YandexAuthSession? {
        relay.currentValue
    }

    init(session: YandexAuthSession? = nil) {
        relay = AsyncValueRelay(session)
    }

    func observeSession() -> AsyncStream<YandexAuthSession?> {
        relay.stream()
    }

    func save(_ session: YandexAuthSession) async throws {
        relay.yield(session)
    }

    func clear() async throws {
        relay.yield(nil)
    }
}

private final class InMemorySecureStore: SecureKeyValueStore, @unchecked Sendable {
    private var storage: [String: Data] = [:]

    func data(forKey key: String) throws -> Data? {
        storage[key]
    }

    func set(_ data: Data, forKey key: String) throws {
        storage[key] = data
    }

    func removeValue(forKey key: String) throws {
        storage.removeValue(forKey: key)
    }
}

private struct FakeDeviceMetadataProvider: YandexDeviceMetadataProviding {
    private let secureStore: SecureKeyValueStore

    init(secureStore: SecureKeyValueStore) {
        self.secureStore = secureStore
    }

    func deviceId() async throws -> YandexDeviceId {
        if let data = try secureStore.data(forKey: "tests.device-id"),
           let value = String(data: data, encoding: .utf8) {
            return YandexDeviceId(rawValue: value)
        }

        let value = "device-123"
        try secureStore.set(Data(value.utf8), forKey: "tests.device-id")
        return YandexDeviceId(rawValue: value)
    }

}

private struct FakeAuthorizationStateGenerator: AuthorizationStateGenerating {
    func generate() throws -> AuthorizationStatePayload {
        AuthorizationStatePayload(state: "state-123")
    }
}

private final class FakeYandexOAuthAPI: YandexOAuthAPI, @unchecked Sendable {
    private let userIdentity: YandexUserIdentity

    init(
        userIdentity: YandexUserIdentity
    ) {
        self.userIdentity = userIdentity
    }

    func fetchUserIdentity(
        accessToken: YandexAccessToken
    ) async throws -> YandexUserIdentity {
        userIdentity
    }
}
