package com.mplayeraudio.feature.auth.yamusic

import com.mplayeraudio.core.domain.yandexauth.YandexAuthRepository
import com.mplayeraudio.core.domain.yandexauth.YandexAuthSession
import com.mplayeraudio.core.domain.yandexauth.YandexAuthStatus
import com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationRequest
import com.mplayeraudio.core.domain.yandexauth.YandexAuthorizationResponseType
import com.mplayeraudio.core.domain.yandexauth.YandexAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YandexMusicAuthCardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onLoginClicked stores embedded auth request and stops loading after webview opens`() = runTest(dispatcher) {
        val repository = FakeYandexAuthRepository()
        val viewModel = viewModel(repository)

        viewModel.onLoginClicked()
        advanceUntilIdle()

        assertEquals(
            YandexAuthorizationRequest(
                url = "https://oauth.yandex.ru/authorize",
                callbackUrlPrefix = "https://music.yandex.ru/",
                responseType = YandexAuthorizationResponseType.Token,
            ),
            viewModel.state.value.authorizationRequest,
        )
        assertFalse(viewModel.state.value.isLoading)
        viewModel.dispose()
    }

    @Test
    fun `failed auth status emits toast effect`() = runTest(dispatcher) {
        val repository = FakeYandexAuthRepository()
        val viewModel = viewModel(repository)
        val effectDeferred = async { viewModel.effects.first { it is YandexMusicAuthCardEffect.ShowToast } }

        repository.status.value = YandexAuthStatus.Failed(
            error = YandexAuthException.ProviderError(
                code = "oauth_failed",
                description = "OAuth failed",
            ),
        )
        advanceUntilIdle()

        assertEquals(
            YandexMusicAuthCardEffect.ShowToast("OAuth failed"),
            effectDeferred.await(),
        )
        assertFalse(viewModel.state.value.isLoading)
        viewModel.dispose()
    }

    @Test
    fun `session flow marks account as authorized`() = runTest(dispatcher) {
        val repository = FakeYandexAuthRepository()
        val viewModel = viewModel(repository)

        repository.session.value = YandexAuthSession(
            accessToken = com.mplayeraudio.core.domain.yandexauth.YandexAccessToken("token"),
            refreshToken = null,
            tokenType = "bearer",
            expiresAt = null,
            scopes = emptySet(),
            deviceId = com.mplayeraudio.core.domain.yandexauth.YandexDeviceId("device"),
            user = com.mplayeraudio.core.domain.yandexauth.YandexUserIdentity(
                id = com.mplayeraudio.core.domain.yandexauth.YandexUserId("1"),
                login = "user",
                displayName = "User",
                email = null,
                avatarId = null,
            ),
            clientId = com.mplayeraudio.core.domain.yandexauth.YandexClientId("client"),
        )
        repository.status.value = YandexAuthStatus.Authorized(repository.session.value!!)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isAuthorized)
        assertFalse(viewModel.state.value.isLoading)
        viewModel.dispose()
    }

    private fun viewModel(repository: FakeYandexAuthRepository): YandexMusicAuthCardViewModel {
        return YandexMusicAuthCardViewModel(
            startYandexAuthorization = StartYandexAuthorizationUseCase(repository),
            completeYandexAuthorization = CompleteYandexAuthorizationUseCase(repository),
            observeYandexSession = ObserveYandexSessionUseCase(repository),
            observeYandexAuthStatus = ObserveYandexAuthStatusUseCase(repository),
        )
    }
}

private class FakeYandexAuthRepository : YandexAuthRepository {
    val session = MutableStateFlow<YandexAuthSession?>(null)
    val status = MutableStateFlow<YandexAuthStatus>(YandexAuthStatus.Unauthorized)

    override fun observeSession(): Flow<YandexAuthSession?> = session

    override fun observeStatus(): Flow<YandexAuthStatus> = status

    override suspend fun createAuthorizationRequest(): YandexAuthorizationRequest {
        status.value = YandexAuthStatus.Authorizing
        return YandexAuthorizationRequest(
            url = "https://oauth.yandex.ru/authorize",
            callbackUrlPrefix = "https://music.yandex.ru/",
            responseType = YandexAuthorizationResponseType.Token,
        )
    }

    override suspend fun completeAuthorization(callbackUri: String): YandexAuthSession {
        error("Not needed in this test")
    }

    override suspend fun logout() {
        session.value = null
        status.value = YandexAuthStatus.Unauthorized
    }
}
