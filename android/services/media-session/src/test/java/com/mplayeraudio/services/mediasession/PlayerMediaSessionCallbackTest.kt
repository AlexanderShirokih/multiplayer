package com.mplayeraudio.services.mediasession

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerMediaSessionCallbackTest {

    @Test
    fun `resolveSessionLocalUri keeps remote item without uri`() {
        val resolvedUri = resolveSessionLocalUri(
            hasLocalConfiguration = false,
            sourceType = MediaItemSourceTypeRemote,
            localUri = null,
        )

        assertNull(resolvedUri)
    }

    @Test
    fun `resolveSessionLocalUri returns local uri when session needs one`() {
        val resolvedUri = resolveSessionLocalUri(
            hasLocalConfiguration = false,
            sourceType = MediaItemSourceTypeLocal,
            localUri = "content://media/external/audio/media/7",
        )

        assertEquals("content://media/external/audio/media/7", resolvedUri)
    }
}
