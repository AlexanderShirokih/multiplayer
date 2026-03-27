package com.mplayeraudio.app

import com.mplayeraudio.core.domain.musicprovider.AuthorizedMusicProvider
import com.mplayeraudio.core.domain.musicprovider.MusicProviderAuthorizationRepository
import kotlinx.coroutines.flow.Flow

class DefaultObserveAuthorizedMusicProviderUseCase(
    private val repository: MusicProviderAuthorizationRepository,
) : ObserveAuthorizedMusicProviderUseCase {

    override fun currentAuthorizedProvider(): AuthorizedMusicProvider? {
        return repository.currentAuthorizedProvider()
    }

    override fun invoke(): Flow<AuthorizedMusicProvider?> = repository.observeAuthorizedProvider()
}
