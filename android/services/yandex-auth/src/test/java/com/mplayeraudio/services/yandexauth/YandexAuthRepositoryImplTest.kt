package com.mplayeraudio.services.yandexauth

import com.mplayeraudio.core.domain.yandexauth.YandexAccessToken
import com.mplayeraudio.core.domain.yandexauth.YandexAuthException
import com.mplayeraudio.core.domain.yandexauth.YandexAuthSession
import com.mplayeraudio.core.domain.yandexauth.YandexAuthStatus
import com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType
import com.mplayeraudio.core.domain.yandexauth.YandexClientId
import com.mplayeraudio.core.domain.yandexauth.YandexDeviceId
import com.mplayeraudio.core.domain.yandexauth.YandexRefreshToken
import com.mplayeraudio.core.domain.yandexauth.YandexUserId
import com.mplayeraudio.core.domain.yandexauth.YandexUserIdentity
import com.mplayeraudio.services.yandexauth.internal.DeviceMetadataProvider
import com.mplayeraudio.services.yandexauth.internal.OAuthTokenPayload
import com.mplayeraudio.services.yandexauth.internal.ParsedAuthorizationCallback
import com.mplayeraudio.services.yandexauth.internal.PendingYandexAuthorization
import com.mplayeraudio.services.yandexauth.internal.PkceGenerator
import com.mplayeraudio.services.yandexauth.internal.PkcePayload
import com.mplayeraudio.services.yandexauth.internal.YandexAuthUrlBuilder
import com.mplayeraudio.services.yandexauth.internal.YandexAuthorizationCallbackParser
import com.mplayeraudio.services.yandexauth.internal.YandexTokenRefresher
import com.mplayeraudio.services.yandexauth.internal.network.YandexOAuthApi
import com.mplayeraudio.services.yandexauth.internal.storage.YandexPendingAuthStore
import com.mplayeraudio.services.yandexauth.internal.storage.YandexSessionStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexAuthRepositoryImplTest {
    private val fixedNow = Instant.parse("2026-03-27T12:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val config = YandexOAuthConfig(
        clientId = YandexClientId("client-id"),
        clientSecret = "client-secret",
        redirectUri = "multiplayer://oauth/yandex",
        deviceName = "MultiPlayer",
    )
    private val user = YandexUserIdentity(
        id = YandexUserId("42"),
        login = "music-user",
        displayName = "Music User",
        email = "music@example.com",
        avatarId = "avatar-id",
    )

    @Test
    fun `completeAuthorization saves session and emits authorized state`() = runTest {
        val sessionStore = FakeSessionStore()
        val pendingAuthStore = FakePendingAuthStore()
        val oauthApi = FakeYandexOAuthApi(
            exchangePayload = OAuthTokenPayload(
                tokenType = "bearer",
                accessToken = YandexAccessToken("access-token"),
                refreshToken = YandexRefreshToken("refresh-token"),
                expiresInSeconds = 3600,
                scopes = setOf("login:info"),
            ),
            userIdentity = user,
        )
        val repository = repository(
            oauthApi = oauthApi,
            sessionStore = sessionStore,
            pendingAuthStore = pendingAuthStore,
        )

        repository.createAuthorizationRequest()
        val session = repository.completeAuthorization(
            callbackUri = "multiplayer://oauth/yandex?code=code-123&state=state-123",
        )

        assertEquals("access-token", session.accessToken.value)
        assertEquals(session, repository.observeSession().first())
        assertEquals(
            YandexAuthStatus.Authorized(session),
            repository.observeStatus().first(),
        )
        assertNull(pendingAuthStore.get())
        assertEquals(1, oauthApi.exchangeCalls)
    }

    @Test
    fun `completeAuthorization rejects invalid callback state`() = runTest {
        val repository = repository()

        repository.createAuthorizationRequest()

        val exception = runCatching {
            repository.completeAuthorization(
                callbackUri = "multiplayer://oauth/yandex?code=code-123&state=unexpected-state",
            )
        }.exceptionOrNull()

        assertTrue(exception is YandexAuthException.InvalidCallbackState)
    }

    @Test
    fun `completeAuthorization accepts access token fragment callback`() = runTest {
        val sessionStore = FakeSessionStore()
        val oauthApi = FakeYandexOAuthApi(userIdentity = user)
        val repository = repository(
            config = config.copy(
                authorizationClientId = YandexClientId("music-client-id"),
                authorizationRedirectUri = "https://music.yandex.ru/",
                authorizationResponseType = YandexAuthorizationResponseType.Token,
            ),
            oauthApi = oauthApi,
            sessionStore = sessionStore,
        )

        repository.createAuthorizationRequest()
        val callbackUri =
            "https://music.yandex.ru/" +
                "#access_token=music-token&token_type=bearer&expires_in=31536000&state=state-123"
        val session = repository.completeAuthorization(
            callbackUri = callbackUri,
        )

        assertEquals("music-token", session.accessToken.value)
        assertEquals("music-client-id", session.clientId.value)
        assertEquals(0, oauthApi.exchangeCalls)
        assertEquals(session, sessionStore.get())
    }

    @Test
    fun `getValidAccessToken refreshes expired session and persists new token`() = runTest {
        val sessionStore = FakeSessionStore(
            session = expiredSession(accessToken = "stale-token", refreshToken = "refresh-token"),
        )
        val oauthApi = FakeYandexOAuthApi(
            refreshPayload = OAuthTokenPayload(
                tokenType = "bearer",
                accessToken = YandexAccessToken("fresh-token"),
                refreshToken = YandexRefreshToken("fresh-refresh-token"),
                expiresInSeconds = 7200,
                scopes = setOf("login:info"),
            ),
            userIdentity = user,
        )
        val repository = repository(
            oauthApi = oauthApi,
            sessionStore = sessionStore,
        )

        val accessToken = repository.getValidAccessToken()

        assertEquals("fresh-token", accessToken)
        assertEquals("fresh-token", sessionStore.get()?.accessToken?.value)
        assertEquals(1, oauthApi.refreshCalls)
    }

    @Test
    fun `getValidAccessToken clears session when refresh fails`() = runTest {
        val sessionStore = FakeSessionStore(
            session = expiredSession(accessToken = "stale-token", refreshToken = "refresh-token"),
        )
        val oauthApi = FakeYandexOAuthApi(
            refreshException = YandexAuthException.ProviderError(
                code = "invalid_grant",
                description = "Refresh token is invalid.",
            ),
            userIdentity = user,
        )
        val repository = repository(
            oauthApi = oauthApi,
            sessionStore = sessionStore,
        )

        val exception = runCatching {
            repository.getValidAccessToken(forceRefresh = true)
        }.exceptionOrNull()

        assertTrue(exception is YandexAuthException.RefreshFailed)
        assertNull(sessionStore.get())
        assertEquals(YandexAuthStatus.Unauthorized, repository.observeStatus().first())
    }

    @Test
    fun `logout clears session even when revoke fails`() = runTest {
        val sessionStore = FakeSessionStore(
            session = validSession(accessToken = "active-token", refreshToken = "refresh-token"),
        )
        val oauthApi = FakeYandexOAuthApi(
            revokeException = YandexAuthException.ProviderError(
                code = "server_error",
                description = "Revoke failed.",
            ),
            userIdentity = user,
        )
        val repository = repository(
            oauthApi = oauthApi,
            sessionStore = sessionStore,
        )

        repository.logout()

        assertNull(sessionStore.get())
        assertEquals(YandexAuthStatus.Unauthorized, repository.observeStatus().first())
    }

    private fun repository(
        config: YandexOAuthConfig = this.config,
        oauthApi: FakeYandexOAuthApi = FakeYandexOAuthApi(userIdentity = user),
        sessionStore: FakeSessionStore = FakeSessionStore(),
        pendingAuthStore: FakePendingAuthStore = FakePendingAuthStore(),
    ): YandexAuthRepositoryImpl {
        return YandexAuthRepositoryImpl(
            config = config,
            oauthApi = oauthApi,
            sessionStore = sessionStore,
            pendingAuthStore = pendingAuthStore,
            deviceMetadataProvider = FakeDeviceMetadataProvider(),
            pkceGenerator = FakePkceGenerator(),
            authUrlBuilder = YandexAuthUrlBuilder(),
            callbackParser = YandexAuthorizationCallbackParser(),
            tokenRefresher = YandexTokenRefresher(
                oauthApi = oauthApi,
                config = config,
                clock = clock,
            ),
            clock = clock,
        )
    }

    private fun validSession(
        accessToken: String,
        refreshToken: String,
    ): YandexAuthSession {
        return YandexAuthSession(
            accessToken = YandexAccessToken(accessToken),
            refreshToken = YandexRefreshToken(refreshToken),
            tokenType = "bearer",
            expiresAt = fixedNow.plusSeconds(3600),
            scopes = setOf("login:info"),
            deviceId = YandexDeviceId("device-123"),
            user = user,
            clientId = YandexClientId("client-id"),
        )
    }

    private fun expiredSession(
        accessToken: String,
        refreshToken: String,
    ): YandexAuthSession {
        return validSession(accessToken = accessToken, refreshToken = refreshToken).copy(
            expiresAt = fixedNow.minusSeconds(60),
        )
    }
}

private class FakePkceGenerator : PkceGenerator {
    override fun generate(): PkcePayload {
        return PkcePayload(
            verifier = "verifier-123",
            challenge = "challenge-456",
            state = "state-123",
        )
    }
}

private class FakeDeviceMetadataProvider : DeviceMetadataProvider {
    override suspend fun getDeviceId(): YandexDeviceId = YandexDeviceId("device-123")

    override fun getDeviceName(): String = "Pixel 9"
}

private class FakeSessionStore(
    session: YandexAuthSession? = null,
) : YandexSessionStore {
    private val backingFlow = MutableStateFlow(session)

    override val sessionFlow: StateFlow<YandexAuthSession?> = backingFlow

    override suspend fun get(): YandexAuthSession? = backingFlow.value

    override suspend fun save(session: YandexAuthSession) {
        backingFlow.value = session
    }

    override suspend fun clear() {
        backingFlow.value = null
    }
}

private class FakePendingAuthStore : YandexPendingAuthStore {
    private var pendingAuthorization: PendingYandexAuthorization? = null

    override suspend fun get(): PendingYandexAuthorization? = pendingAuthorization

    override suspend fun save(pendingAuthorization: PendingYandexAuthorization) {
        this.pendingAuthorization = pendingAuthorization
    }

    override suspend fun clear() {
        pendingAuthorization = null
    }
}

private class FakeYandexOAuthApi(
    private val exchangePayload: OAuthTokenPayload = OAuthTokenPayload(
        tokenType = "bearer",
        accessToken = YandexAccessToken("access-token"),
        refreshToken = YandexRefreshToken("refresh-token"),
        expiresInSeconds = 3600,
        scopes = setOf("login:info"),
    ),
    private val refreshPayload: OAuthTokenPayload = OAuthTokenPayload(
        tokenType = "bearer",
        accessToken = YandexAccessToken("refreshed-token"),
        refreshToken = YandexRefreshToken("refreshed-refresh-token"),
        expiresInSeconds = 3600,
        scopes = setOf("login:info"),
    ),
    private val userIdentity: YandexUserIdentity,
    private val refreshException: YandexAuthException? = null,
    private val revokeException: YandexAuthException? = null,
) : YandexOAuthApi {
    var exchangeCalls: Int = 0
    var refreshCalls: Int = 0

    override suspend fun exchangeAuthorizationCode(
        config: YandexOAuthConfig,
        code: String,
        codeVerifier: String,
        deviceId: String,
        deviceName: String,
    ): OAuthTokenPayload {
        exchangeCalls += 1
        return exchangePayload
    }

    override suspend fun refreshAccessToken(
        config: YandexOAuthConfig,
        refreshToken: YandexRefreshToken,
    ): OAuthTokenPayload {
        refreshCalls += 1
        refreshException?.let { throw it }
        return refreshPayload
    }

    override suspend fun revokeToken(
        config: YandexOAuthConfig,
        accessToken: YandexAccessToken,
    ) {
        revokeException?.let { throw it }
    }

    override suspend fun fetchUserIdentity(accessToken: YandexAccessToken): YandexUserIdentity = userIdentity
}
