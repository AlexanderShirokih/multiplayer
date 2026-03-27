package com.mplayeraudio.core.domain.musicprovider

import kotlinx.coroutines.flow.Flow

enum class AuthorizedMusicProvider {
    YandexMusic,
}

interface MusicProviderAuthorizationRepository {
    fun currentAuthorizedProvider(): AuthorizedMusicProvider?

    fun observeAuthorizedProvider(): Flow<AuthorizedMusicProvider?>
}
