import Foundation

public final class YandexAuthRepositoryImpl:
    YandexAuthRepository,
    YandexAccessTokenProvider,
    MusicProviderAuthorizationRepository,
    @unchecked Sendable {
    private let config: YandexOAuthConfig
    private let oauthAPI: YandexOAuthAPI
    private let sessionStore: YandexSessionStore
    private let pendingAuthorizationStore: YandexPendingAuthorizationStore
    private let deviceMetadataProvider: YandexDeviceMetadataProviding
    private let stateGenerator: AuthorizationStateGenerating
    private let authorizationURLBuilder: YandexAuthorizationURLBuilding
    private let callbackParser: YandexAuthorizationCallbackParsing
    private let now: @Sendable () -> Date
    private let statusRelay: AsyncValueRelay<YandexAuthStatus>

    public convenience init(
        config: YandexOAuthConfig,
        sessionStore: YandexSessionStore,
        secureStore: SecureKeyValueStore,
        urlSession: URLSession = .shared,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.init(
            config: config,
            oauthAPI: URLSessionYandexOAuthAPI(session: urlSession),
            sessionStore: sessionStore,
            pendingAuthorizationStore: KeychainYandexPendingAuthorizationStore(secureStore: secureStore),
            deviceMetadataProvider: AppleDeviceMetadataProvider(secureStore: secureStore),
            stateGenerator: SecureAuthorizationStateGenerator(),
            authorizationURLBuilder: YandexAuthorizationURLBuilder(),
            callbackParser: YandexAuthorizationCallbackParser(),
            now: now
        )
    }

    init(
        config: YandexOAuthConfig,
        oauthAPI: YandexOAuthAPI,
        sessionStore: YandexSessionStore,
        pendingAuthorizationStore: YandexPendingAuthorizationStore,
        deviceMetadataProvider: YandexDeviceMetadataProviding,
        stateGenerator: AuthorizationStateGenerating,
        authorizationURLBuilder: YandexAuthorizationURLBuilding,
        callbackParser: YandexAuthorizationCallbackParsing,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.config = config
        self.oauthAPI = oauthAPI
        self.sessionStore = sessionStore
        self.pendingAuthorizationStore = pendingAuthorizationStore
        self.deviceMetadataProvider = deviceMetadataProvider
        self.stateGenerator = stateGenerator
        self.authorizationURLBuilder = authorizationURLBuilder
        self.callbackParser = callbackParser
        self.now = now
        statusRelay = AsyncValueRelay(
            sessionStore.cachedSession.map(YandexAuthStatus.authorized) ?? .unauthorized
        )
    }

    public func currentSession() -> YandexAuthSession? {
        sessionStore.cachedSession
    }

    public func observeSession() -> AsyncStream<YandexAuthSession?> {
        sessionStore.observeSession()
    }

    public func observeStatus() -> AsyncStream<YandexAuthStatus> {
        statusRelay.stream()
    }

    public func currentAuthorizedProvider() -> AuthorizedMusicProvider? {
        currentSession().map { _ in .yandexMusic }
    }

    public func observeAuthorizedProvider() -> AsyncStream<AuthorizedMusicProvider?> {
        let sessionStream = observeSession()
        return AsyncStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            let task = Task {
                for await session in sessionStream {
                    continuation.yield(session.map { _ in .yandexMusic })
                }
                continuation.finish()
            }

            continuation.onTermination = { _ in
                task.cancel()
            }
        }
    }

    public func createAuthorizationRequest() async throws -> YandexAuthorizationRequest {
        try config.requireAuthorizationConfig()
        let deviceId = try await deviceMetadataProvider.deviceId()
        let statePayload = try stateGenerator.generate()
        let authorizationURL = try authorizationURLBuilder.buildAuthorizationURL(
            config: config,
            state: statePayload.state
        )

        try await pendingAuthorizationStore.save(
            PendingYandexAuthorization(
                state: statePayload.state,
                deviceId: deviceId
            )
        )
        statusRelay.yield(.authorizing)
        return YandexAuthorizationRequest(
            url: authorizationURL,
            callbackURLPrefix: config.authorizationRedirectURL.absoluteString
        )
    }

    public func completeAuthorization(callbackURL: URL) async throws -> YandexAuthSession {
        guard let pendingAuthorization = try await pendingAuthorizationStore.get() else {
            try fail(.missingPendingAuthorization)
        }

        do {
            let callback = callbackParser.parse(callbackURL)
            try callback.throwIfProviderError()
            try callback.requireMatchingState(expectedState: pendingAuthorization.state)
            try await pendingAuthorizationStore.clear()

            let tokenPayload = try callback.requiredTokenPayload()
            let userIdentity = try await oauthAPI.fetchUserIdentity(accessToken: tokenPayload.accessToken)
            let session = tokenPayload.session(
                clientId: config.clientId,
                userIdentity: userIdentity,
                deviceId: pendingAuthorization.deviceId,
                now: now
            )
            try await sessionStore.save(session)
            statusRelay.yield(.authorized(session))
            return session
        } catch let error as YandexAuthException {
            try fail(error)
        } catch {
            try fail(.networkFailure(reason: error.localizedDescription))
        }
    }

    public func cancelAuthorization() async {
        guard statusRelay.currentValue == .authorizing else {
            return
        }

        try? await pendingAuthorizationStore.clear()
        statusRelay.yield(currentSession().map(YandexAuthStatus.authorized) ?? .unauthorized)
    }

    public func logout() async {
        try? await pendingAuthorizationStore.clear()
        try? await sessionStore.clear()
        statusRelay.yield(.unauthorized)
    }

    public func validAccessToken(forceRefresh: Bool = false) async throws -> String {
        let session = try requireSession()
        if !forceRefresh && !session.requiresReauthorization(currentDate: now()) {
            return session.accessToken.rawValue
        }

        try? await sessionStore.clear()
        statusRelay.yield(.unauthorized)
        throw YandexAuthException.refreshFailed(
            reason: "Повторная авторизация требуется заново. Обновление токена не поддерживается."
        )
    }

    private func requireSession() throws -> YandexAuthSession {
        guard let session = currentSession() else {
            throw YandexAuthException.missingSession
        }
        return session
    }

    private func fail(_ error: YandexAuthException) throws -> Never {
        statusRelay.yield(.failed(error))
        throw error
    }
}

private extension OAuthTokenPayload {
    func session(
        clientId: YandexClientId,
        userIdentity: YandexUserIdentity,
        deviceId: YandexDeviceId,
        now: @escaping @Sendable () -> Date
    ) -> YandexAuthSession {
        YandexAuthSession(
            accessToken: accessToken,
            refreshToken: refreshToken,
            tokenType: tokenType,
            expiresAt: expiresInSeconds.map { now().addingTimeInterval(TimeInterval($0)) },
            scopes: scopes,
            deviceId: deviceId,
            user: userIdentity,
            clientId: clientId
        )
    }
}

private extension ParsedAuthorizationCallback {
    func throwIfProviderError() throws {
        guard let error else { return }
        if error == "access_denied" {
            throw YandexAuthException.accessDenied(description: errorDescription)
        }
        throw YandexAuthException.providerError(code: error, description: errorDescription)
    }

    func requireMatchingState(expectedState: String) throws {
        guard state == expectedState else {
            throw YandexAuthException.invalidCallbackState
        }
    }

    func requiredTokenPayload() throws -> OAuthTokenPayload {
        guard let accessToken else {
            throw YandexAuthException.providerError(
                code: "missing_access_token",
                description: "Yandex OAuth callback does not contain an access token."
            )
        }

        return OAuthTokenPayload(
            tokenType: tokenType ?? "bearer",
            accessToken: accessToken,
            refreshToken: nil,
            expiresInSeconds: expiresInSeconds,
            scopes: scopes
        )
    }
}

private extension YandexAuthSession {
    func requiresReauthorization(currentDate: Date) -> Bool {
        guard let expiresAt else {
            return false
        }

        return expiresAt <= currentDate.addingTimeInterval(5 * 60)
    }
}
