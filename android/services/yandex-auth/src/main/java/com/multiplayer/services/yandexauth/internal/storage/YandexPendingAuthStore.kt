package com.multiplayer.services.yandexauth.internal.storage

import com.multiplayer.services.yandexauth.internal.PendingYandexAuthorization

internal interface YandexPendingAuthStore {
    suspend fun get(): PendingYandexAuthorization?

    suspend fun save(pendingAuthorization: PendingYandexAuthorization)

    suspend fun clear()
}
