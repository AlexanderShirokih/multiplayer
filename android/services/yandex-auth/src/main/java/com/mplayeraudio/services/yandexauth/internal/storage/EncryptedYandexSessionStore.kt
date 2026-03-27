package com.mplayeraudio.services.yandexauth.internal.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import com.mplayeraudio.core.domain.yandexauth.YandexAuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class EncryptedYandexSessionStore(
    encryptedPreferencesFactory: EncryptedPreferencesFactory,
    private val codec: YandexSessionCodec,
) : YandexSessionStore {

    private val preferences = encryptedPreferencesFactory.create(PREFERENCES_NAME)
    private val state = MutableStateFlow(codec.decode(preferences.getString(KEY_SESSION, null)))

    override val sessionFlow: StateFlow<YandexAuthSession?> = state.asStateFlow()

    override suspend fun get(): YandexAuthSession? = state.value

    override suspend fun save(session: YandexAuthSession) {
        preferences.edit(commit = true) {
            putString(KEY_SESSION, codec.encode(session))
        }
        state.value = session
    }

    override suspend fun clear() {
        preferences.edit(commit = true) {
            remove(KEY_SESSION)
        }
        state.value = null
    }

    private companion object {
        private const val KEY_SESSION = "yandex_auth_session"
        private const val PREFERENCES_NAME = "yandex_auth_session_preferences"
    }
}
