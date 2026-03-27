package com.mplayeraudio.feature.auth.yamusic

import com.mplayeraudio.core.domain.yandexauth.YandexAuthRepository
import com.mplayeraudio.core.domain.yandexauth.YandexAuthSession
import com.mplayeraudio.core.domain.yandexauth.YandexAuthStatus
import kotlinx.coroutines.flow.Flow

class StartYandexAuthorizationUseCase(
    private val repository: YandexAuthRepository,
) {
    suspend operator fun invoke() = repository.createAuthorizationRequest()
}

class CompleteYandexAuthorizationUseCase(
    private val repository: YandexAuthRepository,
) {
    suspend operator fun invoke(callbackUri: String) = repository.completeAuthorization(callbackUri)
}

class ObserveYandexSessionUseCase(
    private val repository: YandexAuthRepository,
) {
    operator fun invoke(): Flow<YandexAuthSession?> = repository.observeSession()
}

class ObserveYandexAuthStatusUseCase(
    private val repository: YandexAuthRepository,
) {
    operator fun invoke(): Flow<YandexAuthStatus> = repository.observeStatus()
}
