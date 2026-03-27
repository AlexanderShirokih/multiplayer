@testable import AuthFeature
import Foundation
import XCTest

final class YandexAuthRepositoryTests: XCTestCase {
    private let fixedNow = Date(timeIntervalSince1970: 1_775_012_800)

    func testCompleteAuthorizationSavesSessionAndEmitsAuthorizedState() async throws {
        let sessionStore = InMemorySessionStore()
        let secureStore = InMemorySecureStore()
        let oauthAPI = FakeYandexOAuthAPI(
            exchangePayload: OAuthTokenPayload(
                tokenType: "bearer",
                accessToken: YandexAccessToken(rawValue: "access-token"),
                refreshToken: YandexRefreshToken(rawValue: "refresh-token"),
                expiresInSeconds: 3600,
                scopes: ["login:info"]
            ),
            userIdentity: sampleUser
        )
        let repository = makeRepository(
            oauthAPI: oauthAPI,
            sessionStore: sessionStore,
            secureStore: secureStore
        )

        _ = try await repository.createAuthorizationRequest()
        let session = try await repository.completeAuthorization(
            callbackURL: URL(string: "mplayeraudio://oauth/yandex?code=code-123&state=state-123")!
        )

        XCTAssertEqual(session.accessToken.rawValue, "access-token")
        XCTAssertEqual(sessionStore.cachedSession, session)

        var iterator = repository.observeStatus().makeAsyncIterator()
        let authorizedStatus = await iterator.next()
        XCTAssertEqual(authorizedStatus, .authorized(session))
        XCTAssertEqual(oauthAPI.exchangeCalls, 1)
    }

    func testCompleteAuthorizationRejectsInvalidCallbackState() async throws {
        let repository = makeRepository()

        _ = try await repository.createAuthorizationRequest()

        do {
            _ = try await repository.completeAuthorization(
                callbackURL: URL(string: "mplayeraudio://oauth/yandex?code=code-123&state=unexpected-state")!
            )
            XCTFail("Expected invalid callback state error")
        } catch {
            XCTAssertEqual(error as? YandexAuthException, .invalidCallbackState)
        }
    }

    func testValidAccessTokenRefreshesExpiredSession() async throws {
        let sessionStore = InMemorySessionStore(
            session: expiredSession(
                accessToken: "stale-token",
                refreshToken: "refresh-token"
            )
        )
        let oauthAPI = FakeYandexOAuthAPI(
            refreshPayload: OAuthTokenPayload(
                tokenType: "bearer",
                accessToken: YandexAccessToken(rawValue: "fresh-token"),
                refreshToken: YandexRefreshToken(rawValue: "fresh-refresh-token"),
                expiresInSeconds: 7200,
                scopes: ["login:info"]
            ),
            userIdentity: sampleUser
        )
        let repository = makeRepository(
            oauthAPI: oauthAPI,
            sessionStore: sessionStore
        )

        let accessToken = try await repository.validAccessToken()

        XCTAssertEqual(accessToken, "fresh-token")
        XCTAssertEqual(sessionStore.cachedSession?.accessToken.rawValue, "fresh-token")
        XCTAssertEqual(oauthAPI.refreshCalls, 1)
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
                callbackURL: URL(string: "mplayeraudio://oauth/yandex?code=code-123&state=state-123")!
            )
            XCTFail("Expected missing pending authorization after cancellation")
        } catch {
            XCTAssertEqual(error as? YandexAuthException, .missingPendingAuthorization)
        }
    }

    private func makeRepository(
        oauthAPI: FakeYandexOAuthAPI = FakeYandexOAuthAPI(userIdentity: sampleUser),
        sessionStore: InMemorySessionStore = InMemorySessionStore(),
        secureStore: InMemorySecureStore = InMemorySecureStore()
    ) -> YandexAuthRepositoryImpl {
        let config = YandexOAuthConfig(
            clientId: YandexClientId(rawValue: "client-id"),
            clientSecret: "client-secret",
            redirectURL: URL(string: "mplayeraudio://oauth/yandex")!,
            deviceName: "MultiPlayer"
        )
        let fixedNow = self.fixedNow

        return YandexAuthRepositoryImpl(
            config: config,
            oauthAPI: oauthAPI,
            sessionStore: sessionStore,
            pendingAuthorizationStore: KeychainYandexPendingAuthorizationStore(secureStore: secureStore),
            deviceMetadataProvider: FakeDeviceMetadataProvider(secureStore: secureStore),
            pkceGenerator: FakePkceGenerator(),
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
            clientId: YandexClientId(rawValue: "client-id")
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

    func deviceName() -> String {
        "Test Device"
    }
}

private struct FakePkceGenerator: PkceGenerating {
    func generate() throws -> PkcePayload {
        PkcePayload(
            verifier: "verifier-123",
            challenge: "challenge-456",
            state: "state-123"
        )
    }
}

private final class FakeYandexOAuthAPI: YandexOAuthAPI, @unchecked Sendable {
    private let exchangePayload: OAuthTokenPayload?
    private let refreshPayload: OAuthTokenPayload?
    private let userIdentity: YandexUserIdentity
    private let refreshException: YandexAuthException?

    private(set) var exchangeCalls = 0
    private(set) var refreshCalls = 0

    init(
        exchangePayload: OAuthTokenPayload? = nil,
        refreshPayload: OAuthTokenPayload? = nil,
        userIdentity: YandexUserIdentity,
        refreshException: YandexAuthException? = nil
    ) {
        self.exchangePayload = exchangePayload
        self.refreshPayload = refreshPayload
        self.userIdentity = userIdentity
        self.refreshException = refreshException
    }

    func exchangeAuthorizationCode(
        config: YandexOAuthConfig,
        code: String,
        codeVerifier: String,
        deviceId: String,
        deviceName: String
    ) async throws -> OAuthTokenPayload {
        exchangeCalls += 1
        return exchangePayload ?? OAuthTokenPayload(
            tokenType: "bearer",
            accessToken: YandexAccessToken(rawValue: "access-token"),
            refreshToken: YandexRefreshToken(rawValue: "refresh-token"),
            expiresInSeconds: 3600,
            scopes: ["login:info"]
        )
    }

    func refreshAccessToken(
        config: YandexOAuthConfig,
        refreshToken: YandexRefreshToken
    ) async throws -> OAuthTokenPayload {
        refreshCalls += 1
        if let refreshException {
            throw refreshException
        }
        return refreshPayload ?? OAuthTokenPayload(
            tokenType: "bearer",
            accessToken: YandexAccessToken(rawValue: "fresh-token"),
            refreshToken: YandexRefreshToken(rawValue: "fresh-refresh-token"),
            expiresInSeconds: 3600,
            scopes: ["login:info"]
        )
    }

    func revokeToken(
        config: YandexOAuthConfig,
        accessToken: YandexAccessToken
    ) async throws {}

    func fetchUserIdentity(
        accessToken: YandexAccessToken
    ) async throws -> YandexUserIdentity {
        userIdentity
    }
}
