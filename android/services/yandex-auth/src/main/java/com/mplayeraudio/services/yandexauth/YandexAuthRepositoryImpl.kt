package com.mplayeraudio.services.yandexauth

import com.mplayeraudio.core.domain.yandexauth.YandexAccessTokenProvider
import com.mplayeraudio.core.domain.yandexauth.YandexAuthException
import com.mplayeraudio.core.domain.yandexauth.YandexAuthRepository
import com.mplayeraudio.core.domain.yandexauth.YandexAuthSession
import com.mplayeraudio.core.domain.yandexauth.YandexAuthStatus
import com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationRequest
import com.mplayeraudio.core.domain.yandexauth.YandexClientId
import com.mplayeraudio.core.domain.yandexauth.YandexUserIdentity
import com.mplayeraudio.core.domain.musicprovider.AuthorizedMusicProvider
import com.mplayeraudio.core.domain.musicprovider.MusicProviderAuthorizationRepository
import com.mplayeraudio.services.yandexauth.internal.DeviceMetadataProvider
import com.mplayeraudio.services.yandexauth.internal.PendingYandexAuthorization
import com.mplayeraudio.services.yandexauth.internal.PkceGenerator
import com.mplayeraudio.services.yandexauth.internal.YandexAuthUrlBuilder
import com.mplayeraudio.services.yandexauth.internal.YandexAuthorizationCallbackParser
import com.mplayeraudio.services.yandexauth.internal.YandexTokenRefresher
import com.mplayeraudio.services.yandexauth.internal.network.YandexOAuthApi
import com.mplayeraudio.services.yandexauth.internal.storage.YandexPendingAuthStore
import com.mplayeraudio.services.yandexauth.internal.storage.YandexSessionStore
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("TooManyFunctions")
internal class YandexAuthRepositoryImpl(
    private val config: YandexOAuthConfig,
    private val oauthApi: YandexOAuthApi,
    private val sessionStore: YandexSessionStore,
    private val pendingAuthStore: YandexPendingAuthStore,
    private val deviceMetadataProvider: DeviceMetadataProvider,
    private val pkceGenerator: PkceGenerator,
    private val authUrlBuilder: YandexAuthUrlBuilder,
    private val callbackParser: YandexAuthorizationCallbackParser,
    private val tokenRefresher: YandexTokenRefresher,
    private val clock: Clock,
) : YandexAuthRepository, YandexAccessTokenProvider, MusicProviderAuthorizationRepository {

    private val refreshMutex = Mutex()
    private val status = MutableStateFlow(initialStatus())

    override fun observeSession(): Flow<YandexAuthSession?> = sessionStore.sessionFlow

    override fun observeStatus(): Flow<YandexAuthStatus> = status.asStateFlow()

    override fun currentAuthorizedProvider(): AuthorizedMusicProvider? {
        return sessionStore.sessionFlow.value?.let { AuthorizedMusicProvider.YandexMusic }
    }

    override fun observeAuthorizedProvider(): Flow<AuthorizedMusicProvider?> {
        return sessionStore.sessionFlow
            .map { session -> session?.let { AuthorizedMusicProvider.YandexMusic } }
            .distinctUntilChanged()
    }

    override fun accessTokenFlow(): Flow<String?> {
        return sessionStore.sessionFlow
            .map { session -> session?.accessToken?.value }
            .distinctUntilChanged()
    }

    override suspend fun createAuthorizationRequest(): YandexAuthorizationRequest {
        config.requireAuthorizationConfig()
        val deviceId = deviceMetadataProvider.getDeviceId()
        val deviceName = deviceMetadataProvider.getDeviceName()
        val pkcePayload = pkceGenerator.generate()
        val request = YandexAuthorizationRequest(
            url = authUrlBuilder.buildAuthorizationUrl(
                config = config,
                state = pkcePayload.state,
                codeChallenge = pkcePayload.challenge,
                deviceId = deviceId,
                deviceName = deviceName,
            ),
        )
        pendingAuthStore.save(
            pendingAuthorization = PendingYandexAuthorization(
                state = pkcePayload.state,
                codeVerifier = pkcePayload.verifier,
                deviceId = deviceId,
                deviceName = deviceName,
                requestedAt = clock.instant(),
            ),
        )
        status.value = YandexAuthStatus.Authorizing
        return request
    }

    override suspend fun completeAuthorization(callbackUri: String): YandexAuthSession {
        val pendingAuthorization = pendingAuthStore.get()
            ?: return fail(YandexAuthException.MissingPendingAuthorization)

        return try {
            val callback = callbackParser.parse(callbackUri)
            pendingAuthStore.clear()
            callback.throwIfProviderError()
            val code = callback.requireAuthorizationCode()
            callback.requireMatchingState(expectedState = pendingAuthorization.state)

            val tokenPayload = oauthApi.exchangeAuthorizationCode(
                config = config,
                code = code,
                codeVerifier = pendingAuthorization.codeVerifier,
                deviceId = pendingAuthorization.deviceId.value,
                deviceName = pendingAuthorization.deviceName,
            )
            val userIdentity = oauthApi.fetchUserIdentity(tokenPayload.accessToken)
            val session = tokenPayload.toSession(
                userIdentity = userIdentity,
                deviceId = pendingAuthorization.deviceId,
            )
            sessionStore.save(session)
            status.value = YandexAuthStatus.Authorized(session)
            session
        } catch (exception: YandexAuthException) {
            fail(exception)
        }
    }

    override suspend fun logout() {
        val existingSession = sessionStore.get()
        if (existingSession != null && config.clientSecret.isNotBlank()) {
            runCatching {
                oauthApi.revokeToken(
                    config = config,
                    accessToken = existingSession.accessToken,
                )
            }
        }

        pendingAuthStore.clear()
        sessionStore.clear()
        status.value = YandexAuthStatus.Unauthorized
    }

    override suspend fun getValidAccessToken(forceRefresh: Boolean): String {
        val session = requireSession()
        if (!forceRefresh && !tokenRefresher.shouldRefresh(session)) {
            return session.accessToken.value
        }

        return refreshMutex.withLock {
            val currentSession = requireSession()
            if (!forceRefresh && !tokenRefresher.shouldRefresh(currentSession)) {
                return@withLock currentSession.accessToken.value
            }

            try {
                config.requireRefreshConfig()
                val refreshedSession = tokenRefresher.refresh(currentSession)
                sessionStore.save(refreshedSession)
                status.value = YandexAuthStatus.Authorized(refreshedSession)
                refreshedSession.accessToken.value
            } catch (exception: YandexAuthException.RefreshFailed) {
                sessionStore.clear()
                status.value = YandexAuthStatus.Unauthorized
                handleRefreshFailure(exception)
            }
        }
    }

    private fun initialStatus(): YandexAuthStatus {
        val session = sessionStore.sessionFlow.value
        return if (session != null) {
            YandexAuthStatus.Authorized(session)
        } else {
            YandexAuthStatus.Unauthorized
        }
    }

    private fun fail(error: YandexAuthException): Nothing {
        status.value = YandexAuthStatus.Failed(error)
        throw error
    }

    private suspend fun requireSession(): YandexAuthSession {
        return sessionStore.get() ?: throw YandexAuthException.MissingSession
    }

    private fun handleRefreshFailure(exception: YandexAuthException.RefreshFailed): Nothing {
        throw exception
    }

    private fun com.mplayeraudio.services.yandexauth.internal.OAuthTokenPayload.toSession(
        userIdentity: YandexUserIdentity,
        deviceId: com.mplayeraudio.core.domain.yandexauth.YandexDeviceId,
    ): YandexAuthSession {
        return YandexAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            tokenType = tokenType,
            expiresAt = expiresInSeconds?.let { clock.instant().plusSeconds(it) },
            scopes = scopes,
            deviceId = deviceId,
            user = userIdentity,
            clientId = YandexClientId(config.clientId.value),
        )
    }

    private fun com.mplayeraudio.services.yandexauth.internal.ParsedAuthorizationCallback.throwIfProviderError() {
        val providerError = error ?: return
        if (providerError == "access_denied") {
            throw YandexAuthException.AccessDenied(errorDescription)
        }
        throw YandexAuthException.ProviderError(
            code = providerError,
            description = errorDescription,
        )
    }

    private fun com.mplayeraudio.services.yandexauth.internal.ParsedAuthorizationCallback.requireAuthorizationCode():
        String {
        return code ?: throw YandexAuthException.MissingAuthorizationCode
    }

    private fun com.mplayeraudio.services.yandexauth.internal.ParsedAuthorizationCallback.requireMatchingState(
        expectedState: String,
    ) {
        if (state != expectedState) {
            throw YandexAuthException.InvalidCallbackState
        }
    }
}
