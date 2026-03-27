package com.mplayeraudio.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mplayeraudio.core.domain.musicprovider.AuthorizedMusicProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppRootState(
    val destination: AppDestination = AppDestination.Auth,
)

enum class AppDestination {
    Auth,
    Player,
}

class AppRootViewModel(
    observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase,
) : ViewModel() {
    val state: StateFlow<AppRootState> = observeAuthorizedMusicProvider()
        .map { provider ->
            AppRootState(
                destination = provider.toDestination(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppRootState(
                destination = observeAuthorizedMusicProvider.currentAuthorizedProvider().toDestination(),
            ),
        )

    private fun AuthorizedMusicProvider?.toDestination(): AppDestination {
        return when(this) {
            null -> {
                AppDestination.Auth
            }

            else -> {
                AppDestination.Player
            }
        }
    }
}
