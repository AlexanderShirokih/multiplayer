package com.multiplayer.services.yandexauth.internal.storage

import androidx.core.content.edit
import com.multiplayer.services.yandexauth.internal.PendingYandexAuthorization

internal class EncryptedYandexPendingAuthStore(
    encryptedPreferencesFactory: EncryptedPreferencesFactory,
    private val codec: YandexPendingAuthCodec,
) : YandexPendingAuthStore {

    private val preferences = encryptedPreferencesFactory.create(PREFERENCES_NAME)

    override suspend fun get(): PendingYandexAuthorization? {
        return codec.decode(preferences.getString(KEY_PENDING_AUTH, null))
    }

    override suspend fun save(pendingAuthorization: PendingYandexAuthorization) {
        preferences.edit(commit = true) {
            putString(KEY_PENDING_AUTH, codec.encode(pendingAuthorization))
        }
    }

    override suspend fun clear() {
        preferences.edit(commit = true) {
            remove(KEY_PENDING_AUTH)
        }
    }

    private companion object {
        private const val KEY_PENDING_AUTH = "yandex_pending_auth"
        private const val PREFERENCES_NAME = "yandex_auth_pending_preferences"
    }
}
