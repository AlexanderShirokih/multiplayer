package com.mplayeraudio.services.yandexauth.internal.storage

import com.mplayeraudio.services.yandexauth.internal.PendingYandexAuthorization

internal interface YandexPendingAuthStore {
    suspend fun get(): PendingYandexAuthorization?

    suspend fun save(pendingAuthorization: PendingYandexAuthorization)

    suspend fun clear()
}
