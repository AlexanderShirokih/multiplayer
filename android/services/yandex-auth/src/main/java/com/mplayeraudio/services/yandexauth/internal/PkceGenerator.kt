package com.mplayeraudio.services.yandexauth.internal

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal interface PkceGenerator {
    fun generate(): PkcePayload
}

internal class SecurePkceGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
) : PkceGenerator {

    override fun generate(): PkcePayload {
        val verifier = secureRandom.randomToken(size = 64)
        val state = secureRandom.randomToken(size = 32)
        val challenge = verifier.sha256UrlSafe()
        return PkcePayload(
            verifier = verifier,
            challenge = challenge,
            state = state,
        )
    }

    private fun SecureRandom.randomToken(size: Int): String {
        val bytes = ByteArray(size)
        nextBytes(bytes)
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    private fun String.sha256UrlSafe(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)
    }
}
