package com.mplayeraudio.services.yandexauth.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class YandexAuthorizationCallbackParserTest {
    private val parser = YandexAuthorizationCallbackParser()

    @Test
    fun `parse reads access token from fragment`() {
        val callbackUri =
            "https://music.yandex.ru/" +
                "#access_token=token-123&token_type=bearer&expires_in=31536000" +
                "&scope=login%3Ainfo%20music%3Acontent&state=state-42"
        val callback = parser.parse(
            callbackUri,
        )

        assertEquals("token-123", callback.accessToken?.value)
        assertEquals("bearer", callback.tokenType)
        assertEquals(31536000L, callback.expiresInSeconds)
        assertEquals(setOf("login:info", "music:content"), callback.scopes)
        assertEquals("state-42", callback.state)
    }
}
