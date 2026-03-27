package com.mplayeraudio.core.domain.yandexauth

import kotlinx.coroutines.flow.Flow

interface YandexAccessTokenProvider {
    fun accessTokenFlow(): Flow<String?>

    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String
}
