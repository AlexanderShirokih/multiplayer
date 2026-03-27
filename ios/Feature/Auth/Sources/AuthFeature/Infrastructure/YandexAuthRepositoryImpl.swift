import Foundation

public final class YandexAuthRepositoryImpl: YandexAuthRepository, YandexAccessTokenProvider, MusicProviderAuthorizationRepository, @unchecked Sendable {
    private let config: YandexOAuthConfig
    private let oauthAPI: YandexOAuthAPI
    private let sessionStore: YandexSessionStore
    private let pendingAuthorizationStore: YandexPendingAuthorizationStore
    private let deviceMetadataProvider: YandexDeviceMetadataProviding
    private let pkceGenerator: PkceGenerating
    private let authorizationURLBuilder: YandexAuthorizationURLBuilding
    private let callbackParser: YandexAuthorizationCallbackParsing
    private let tokenRefresher: YandexTokenRefresher
    private let now: @Sendable () -> Date
    private let statusRelay: AsyncValueRelay<YandexAuthStatus>

    public convenience init(
        config: YandexOAuthConfig,
        sessionStore: YandexSessionStore,
        secureStore: SecureKeyValueStore,
        urlSession: URLSession = .shared,
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.init(
            config: config,
            oauthAPI: URLSessionYandexOAuthAPI(session: urlSession),
            sessionStore: sessionStore,
            pendingAuthorizationStore: KeychainYandexPendingAuthorizationStore(secureStore: secureStore),
            deviceMetadataProvider: AppleDeviceMetadataProvider(
                configuredName: config.deviceName,
                secureStore: secureStore
            ),
            pkceGenerator: SecurePkceGenerator(),
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
        pkceGenerator: PkceGenerating,
        authorizationURLBuilder: YandexAuthorizationURLBuilding,
        callbackParser: YandexAuthorizationCallbackParsing,
        now: @escaping @Sendable () -> Date = Date.init
    ) {
        self.config = config
        self.oauthAPI = oauthAPI
        self.sessionStore = sessionStore
        self.pendingAuthorizationStore = pendingAuthorizationStore
        self.deviceMetadataProvider = deviceMetadataProvider
        self.pkceGenerator = pkceGenerator
        self.authorizationURLBuilder = authorizationURLBuilder
        self.callbackParser = callbackParser
        self.now = now
        tokenRefresher = YandexTokenRefresher(
            oauthAPI: oauthAPI,
            config: config,
            now: now
        )
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
        let deviceName = deviceMetadataProvider.deviceName()
        let pkcePayload = try pkceGenerator.generate()
        let authorizationURL = try authorizationURLBuilder.buildAuthorizationURL(
            config: config,
            state: pkcePayload.state,
            codeChallenge: pkcePayload.challenge,
            deviceId: deviceId,
            deviceName: deviceName
        )

        try await pendingAuthorizationStore.save(
            PendingYandexAuthorization(
                state: pkcePayload.state,
                codeVerifier: pkcePayload.verifier,
                deviceId: deviceId,
                deviceName: deviceName,
                requestedAt: now()
            )
        )
        statusRelay.yield(.authorizing)
        return YandexAuthorizationRequest(url: authorizationURL)
    }

    public func completeAuthorization(callbackURL: URL) async throws -> YandexAuthSession {
        guard let pendingAuthorization = try await pendingAuthorizationStore.get() else {
            try fail(.missingPendingAuthorization)
        }

        do {
            let callback = callbackParser.parse(callbackURL)
            try await pendingAuthorizationStore.clear()
            try callback.throwIfProviderError()
            let code = try callback.requiredAuthorizationCode()
            try callback.requireMatchingState(expectedState: pendingAuthorization.state)

            let tokenPayload = try await oauthAPI.exchangeAuthorizationCode(
                config: config,
                code: code,
                codeVerifier: pendingAuthorization.codeVerifier,
                deviceId: pendingAuthorization.deviceId.rawValue,
                deviceName: pendingAuthorization.deviceName
            )
            let userIdentity = try await oauthAPI.fetchUserIdentity(accessToken: tokenPayload.accessToken)
            let session = tokenPayload.session(
                config: config,
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
        if let existingSession = currentSession(),
           !config.clientSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            try? await oauthAPI.revokeToken(
                config: config,
                accessToken: existingSession.accessToken
            )
        }

        try? await pendingAuthorizationStore.clear()
        try? await sessionStore.clear()
        statusRelay.yield(.unauthorized)
    }

    public func validAccessToken(forceRefresh: Bool = false) async throws -> String {
        let session = try requireSession()
        if !forceRefresh && !tokenRefresher.shouldRefresh(session) {
            return session.accessToken.rawValue
        }

        do {
            try config.requireRefreshConfig()
            let refreshedSession = try await tokenRefresher.refresh(session)
            try await sessionStore.save(refreshedSession)
            statusRelay.yield(.authorized(refreshedSession))
            return refreshedSession.accessToken.rawValue
        } catch let error as YandexAuthException {
            if case .refreshFailed = error {
                try? await sessionStore.clear()
                statusRelay.yield(.unauthorized)
            }
            throw error
        } catch {
            throw YandexAuthException.refreshFailed(reason: error.localizedDescription)
        }
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
        config: YandexOAuthConfig,
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
            clientId: config.clientId
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

    func requiredAuthorizationCode() throws -> String {
        guard let code, !code.isEmpty else {
            throw YandexAuthException.missingAuthorizationCode
        }
        return code
    }

    func requireMatchingState(expectedState: String) throws {
        guard state == expectedState else {
            throw YandexAuthException.invalidCallbackState
        }
    }
}
