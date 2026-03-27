package com.mplayeraudio.services.yandexauth.internal.storage

import com.mplayeraudio.core.domain.yandexauth.YandexAuthSession
import kotlinx.coroutines.flow.StateFlow

internal interface YandexSessionStore {
    val sessionFlow: StateFlow<YandexAuthSession?>

    suspend fun get(): YandexAuthSession?

    suspend fun save(session: YandexAuthSession)

    suspend fun clear()
}
