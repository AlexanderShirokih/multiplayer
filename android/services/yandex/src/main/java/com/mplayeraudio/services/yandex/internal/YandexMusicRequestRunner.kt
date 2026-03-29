package com.mplayeraudio.services.yandex.internal

import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.yandexauth.YandexAccessTokenProvider
import com.mplayeraudio.services.yandex.internal.network.YandexMusicApi

internal class YandexMusicRequestRunner(
    private val accessTokenProvider: YandexAccessTokenProvider,
    private val api: YandexMusicApi,
) {

    @Volatile
    private var cachedUserId: ProviderUserId? = null

    suspend fun <T> withCurrentUserId(
        block: suspend (accessToken: String, userId: ProviderUserId) -> T,
    ): T {
        return withAuthorizedRequest { accessToken ->
            block(accessToken, requireCurrentUserId(accessToken))
        }
    }

    suspend fun <T> withAuthorizedRequest(
        block: suspend (accessToken: String) -> T,
    ): T {
        val currentToken = accessTokenProvider.getValidAccessToken(forceRefresh = false)
        return try {
            block(currentToken)
        } catch (_: MusicLibraryException.Unauthorized) {
            val refreshedToken = accessTokenProvider.getValidAccessToken(forceRefresh = true)
            block(refreshedToken)
        }
    }

    private suspend fun requireCurrentUserId(accessToken: String): ProviderUserId {
        cachedUserId?.let { return it }
        val userId = api.fetchCurrentUser(accessToken).toCurrentUserId()
        cachedUserId = userId
        return userId
    }
}
