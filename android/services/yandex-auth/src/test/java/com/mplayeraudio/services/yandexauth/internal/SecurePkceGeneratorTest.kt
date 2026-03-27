package com.mplayeraudio.services.yandexauth.internal

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurePkceGeneratorTest {
    private val generator = SecurePkceGenerator()

    @Test
    fun `generate creates verifier challenge and state`() {
        val payload = generator.generate()

        assertTrue(payload.verifier.isNotBlank())
        assertTrue(payload.challenge.isNotBlank())
        assertTrue(payload.state.isNotBlank())
        assertNotEquals(payload.verifier, payload.challenge)
        assertTrue(payload.challenge.matches(Regex("[A-Za-z0-9_-]+")))
    }
}
