package com.mplayeraudio.core.domain.yandexauth

import kotlinx.coroutines.flow.Flow

interface YandexAuthRepository {
    fun observeSession(): Flow<YandexAuthSession?>

    fun observeStatus(): Flow<YandexAuthStatus>

    suspend fun createAuthorizationRequest(): YandexAuthorizationRequest

    suspend fun completeAuthorization(callbackUri: String): YandexAuthSession

    suspend fun logout()
}
