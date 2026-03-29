package com.mplayeraudio.core.domain.musiclibrary

interface TrackStreamUrlProvider {
    suspend fun getStreamUrl(trackId: TrackId): String
}
