package com.mplayeraudio.app

import com.mplayeraudio.core.domain.musicprovider.AuthorizedMusicProvider
import kotlinx.coroutines.flow.Flow

interface ObserveAuthorizedMusicProviderUseCase {
    fun currentAuthorizedProvider(): AuthorizedMusicProvider?

    operator fun invoke(): Flow<AuthorizedMusicProvider?>
}
