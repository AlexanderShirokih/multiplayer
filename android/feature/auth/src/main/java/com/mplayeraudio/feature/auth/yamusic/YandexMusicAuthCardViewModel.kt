package com.mplayeraudio.feature.auth.yamusic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mplayeraudio.core.domain.yandexauth.YandexAuthException
import com.mplayeraudio.core.domain.yandexauth.YandexAuthStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class YandexMusicAuthCardState(
    val isLoading: Boolean = false,
    val isAuthorized: Boolean = false,
)

sealed interface YandexMusicAuthCardEffect {
    data class OpenExternalAuth(
        val url: String,
    ) : YandexMusicAuthCardEffect

    data class ShowToast(
        val message: String,
    ) : YandexMusicAuthCardEffect
}

class YandexMusicAuthCardViewModel(
    private val startYandexAuthorization: StartYandexAuthorizationUseCase,
    observeYandexSession: ObserveYandexSessionUseCase,
    observeYandexAuthStatus: ObserveYandexAuthStatusUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(YandexMusicAuthCardState())
    val state: StateFlow<YandexMusicAuthCardState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<YandexMusicAuthCardEffect>()
    val effects: SharedFlow<YandexMusicAuthCardEffect> = _effects.asSharedFlow()

    init {
        observeYandexSession()
            .onEach { session ->
                _state.update { currentState ->
                    currentState.copy(isAuthorized = session != null)
                }
            }
            .launchIn(viewModelScope)

        observeYandexAuthStatus()
            .onEach(::handleAuthStatus)
            .launchIn(viewModelScope)
    }

    fun onLoginClicked() {
        viewModelScope.launch {
            _state.update { currentState -> currentState.copy(isLoading = true) }

            try {
                val request = startYandexAuthorization()
                _effects.emit(YandexMusicAuthCardEffect.OpenExternalAuth(request.url))
            } catch (exception: YandexAuthException) {
                _state.update { currentState -> currentState.copy(isLoading = false) }
                _effects.emit(YandexMusicAuthCardEffect.ShowToast(exception.toUserMessage()))
            }
        }
    }

    fun onBrowserLaunchFailed() {
        _state.update { currentState -> currentState.copy(isLoading = false) }
        viewModelScope.launch {
            _effects.emit(
                YandexMusicAuthCardEffect.ShowToast(
                    "Не удалось открыть страницу авторизации Яндекса.",
                ),
            )
        }
    }

    internal fun dispose() {
        viewModelScope.cancel()
    }

    private fun handleAuthStatus(status: YandexAuthStatus) {
        when (status) {
            YandexAuthStatus.Authorizing -> {
                _state.update { currentState -> currentState.copy(isLoading = true) }
            }

            is YandexAuthStatus.Authorized -> {
                _state.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        isAuthorized = true,
                    )
                }
            }

            is YandexAuthStatus.Failed -> {
                _state.update { currentState -> currentState.copy(isLoading = false) }
                viewModelScope.launch {
                    _effects.emit(
                        YandexMusicAuthCardEffect.ShowToast(
                            (status.error as? Throwable)?.toUserMessage()
                                ?: "Не удалось авторизоваться через Яндекс.",
                        ),
                    )
                }
            }

            YandexAuthStatus.Unauthorized -> {
                _state.update { currentState ->
                    currentState.copy(isLoading = false)
                }
            }
        }
    }

    private fun Throwable.toUserMessage(): String {
        return when (this) {
            YandexAuthException.MissingConfiguration -> {
                "Не настроен Yandex OAuth. Проверьте client_id, client_secret и redirect URI."
            }

            YandexAuthException.InvalidCallbackState -> {
                "Сессия авторизации устарела. Попробуйте войти ещё раз."
            }

            YandexAuthException.MissingAuthorizationCode -> {
                "Яндекс не вернул код авторизации."
            }

            is YandexAuthException.AccessDenied -> {
                "Доступ к аккаунту Яндекса не был подтверждён."
            }

            else -> {
                message ?: "Не удалось авторизоваться через Яндекс."
            }
        }
    }
}
