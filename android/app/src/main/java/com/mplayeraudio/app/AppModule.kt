package com.mplayeraudio.app

import com.mplayeraudio.core.domain.musicprovider.AuthorizedMusicProvider
import com.mplayeraudio.core.domain.musicprovider.MusicProviderAuthorizationRepository
import kotlinx.coroutines.flow.Flow
import org.koin.dsl.module

fun appModule() = module {
    factory {
        ObserveAuthorizedMusicProviderUseCase(
            repository = get(),
        )
    }
}

class ObserveAuthorizedMusicProviderUseCase(
    private val repository: MusicProviderAuthorizationRepository,
) {
    fun currentAuthorizedProvider(): AuthorizedMusicProvider? {
        return repository.currentAuthorizedProvider()
    }

    operator fun invoke(): Flow<AuthorizedMusicProvider?> = repository.observeAuthorizedProvider()
}
