package com.mplayeraudio.services.yandexauth.internal

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.mplayeraudio.core.domain.yandexauth.YandexDeviceId
import com.mplayeraudio.services.yandexauth.YandexOAuthConfig
import java.util.UUID

internal interface DeviceMetadataProvider {
    suspend fun getDeviceId(): YandexDeviceId

    fun getDeviceName(): String
}

internal class AndroidDeviceMetadataProvider(
    context: Context,
    private val config: YandexOAuthConfig,
) : DeviceMetadataProvider {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun getDeviceId(): YandexDeviceId {
        val storedDeviceId = preferences.getString(KEY_DEVICE_ID, null)
        if (storedDeviceId != null) {
            return YandexDeviceId(storedDeviceId)
        }

        val generatedDeviceId = UUID.randomUUID().toString()
        preferences.edit(commit = true) {
            putString(KEY_DEVICE_ID, generatedDeviceId)
        }
        return YandexDeviceId(generatedDeviceId)
    }

    override fun getDeviceName(): String {
        val configuredName = config.deviceName?.trim().orEmpty()
        if (configuredName.isNotEmpty()) {
            return configuredName.take(MAX_DEVICE_NAME_LENGTH)
        }

        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val systemVersion = Build.VERSION.RELEASE.orEmpty().trim()
        val deviceName = listOf(manufacturer, model, "Android $systemVersion")
            .filter(String::isNotBlank)
            .joinToString(separator = " ")
            .ifBlank { "Android device" }
        return deviceName.take(MAX_DEVICE_NAME_LENGTH)
    }

    private companion object {
        private const val KEY_DEVICE_ID = "yandex_oauth_device_id"
        private const val MAX_DEVICE_NAME_LENGTH = 100
        private const val PREFERENCES_NAME = "yandex_auth_device_metadata"
    }
}
