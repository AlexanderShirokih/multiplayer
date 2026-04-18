package com.mplayeraudio.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class AppRootState(
    val destination: AppDestination = AppDestination.Auth,
)

enum class AppDestination {
    Auth,
    Main,
}

class AppRootViewModel(
    observeAuthorizedMusicProvider: ObserveAuthorizedMusicProviderUseCase,
) : ViewModel() {
    private val userOptedIntoDeviceOnly = MutableStateFlow(false)

    val state: StateFlow<AppRootState> = combine(
        observeAuthorizedMusicProvider(),
        userOptedIntoDeviceOnly,
    ) { provider, isDeviceOnly ->
        AppRootState(
            destination = if (provider != null || isDeviceOnly) {
                AppDestination.Main
            } else {
                AppDestination.Auth
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = AppRootState(
            destination = if (observeAuthorizedMusicProvider.currentAuthorizedProvider() != null) {
                AppDestination.Main
            } else {
                AppDestination.Auth
            },
        ),
    )

    init {
        observeAuthorizedMusicProvider()
            .onEach { provider ->
                if (provider != null) {
                    userOptedIntoDeviceOnly.value = false
                }
            }
            .launchIn(viewModelScope)
    }

    fun openDeviceOnlyMode() {
        userOptedIntoDeviceOnly.update { true }
    }
}
