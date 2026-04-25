package com.mplayeraudio.services.userplaylists

import java.net.URI

data class UrlTrackMetadata(
    val title: String,
    val artist: String?,
)

class UrlMetadataExtractor {
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    fun parse(url: String): UrlTrackMetadata? {
        val uri = try {
            URI.create(url)
        } catch (e: Exception) {
            // Ignore parse errors
            null
        }
        
        val isHttp = uri?.scheme == "http" || uri?.scheme == "https"
        val host = uri?.host
        val path = uri?.path ?: ""
        
        val lastSegment = if (path.isEmpty() || path == "/") {
            "Unknown Track"
        } else {
            path.trimEnd('/').substringAfterLast('/')
        }
        
        return if (isHttp && host != null) {
            UrlTrackMetadata(
                title = lastSegment,
                artist = host,
            )
        } else {
            null
        }
    }
}
