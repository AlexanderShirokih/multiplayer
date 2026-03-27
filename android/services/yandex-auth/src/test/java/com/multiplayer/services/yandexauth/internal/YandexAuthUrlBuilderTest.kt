package com.multiplayer.services.yandexauth.internal

import com.multiplayer.core.domain.yandexauth.YandexClientId
import com.multiplayer.core.domain.yandexauth.YandexDeviceId
import com.multiplayer.services.yandexauth.YandexOAuthConfig
import io.ktor.http.Url
import org.junit.Assert.assertEquals
import org.junit.Test

class YandexAuthUrlBuilderTest {
    private val builder = YandexAuthUrlBuilder()

    @Test
    fun `buildAuthorizationUrl includes pkce redirect and device metadata`() {
        val url = builder.buildAuthorizationUrl(
            config = YandexOAuthConfig(
                clientId = YandexClientId("client-id"),
                clientSecret = "client-secret",
                redirectUri = "multiplayer://oauth/yandex",
                deviceName = "MultiPlayer",
            ),
            state = "state-123",
            codeChallenge = "challenge-456",
            deviceId = YandexDeviceId("device-789"),
            deviceName = "Pixel 9",
        )

        val parsedUrl = Url(url)
        assertEquals("code", parsedUrl.parameters["response_type"])
        assertEquals("client-id", parsedUrl.parameters["client_id"])
        assertEquals("multiplayer://oauth/yandex", parsedUrl.parameters["redirect_uri"])
        assertEquals("device-789", parsedUrl.parameters["device_id"])
        assertEquals("Pixel 9", parsedUrl.parameters["device_name"])
        assertEquals("state-123", parsedUrl.parameters["state"])
        assertEquals("challenge-456", parsedUrl.parameters["code_challenge"])
        assertEquals("S256", parsedUrl.parameters["code_challenge_method"])
    }
}
