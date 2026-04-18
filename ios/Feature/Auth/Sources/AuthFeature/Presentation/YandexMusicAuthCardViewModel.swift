import CoreDomain
import Foundation
import Observation

public struct YandexMusicAuthCardState: Equatable, Sendable {
    public var isLoading: Bool
    public var isAuthorized: Bool

    public init(
        isLoading: Bool = false,
        isAuthorized: Bool = false
    ) {
        self.isLoading = isLoading
        self.isAuthorized = isAuthorized
    }
}

public enum YandexMusicAuthCardEffect: Equatable, Sendable {
    case presentAuthWebView(YandexAuthorizationRequest)
    case showAlert(String)
}

@Observable
@MainActor
public final class YandexMusicAuthCardViewModel {
    public private(set) var state = YandexMusicAuthCardState()

    private let startYandexAuthorization: StartYandexAuthorizationUseCase
    private let completeYandexAuthorization: CompleteYandexAuthorizationUseCase
    private let cancelYandexAuthorization: CancelYandexAuthorizationUseCase
    private let observeYandexSession: ObserveYandexSessionUseCase
    private let observeYandexAuthStatus: ObserveYandexAuthStatusUseCase
    private let effectEmitter = AsyncEventEmitter<YandexMusicAuthCardEffect>()
    private var observationTask: Task<Void, Never>?
    private var isAwaitingAuthorizationCallback = false

    public init(
        startYandexAuthorization: StartYandexAuthorizationUseCase,
        completeYandexAuthorization: CompleteYandexAuthorizationUseCase,
        cancelYandexAuthorization: CancelYandexAuthorizationUseCase,
        observeYandexSession: ObserveYandexSessionUseCase,
        observeYandexAuthStatus: ObserveYandexAuthStatusUseCase
    ) {
        self.startYandexAuthorization = startYandexAuthorization
        self.completeYandexAuthorization = completeYandexAuthorization
        self.cancelYandexAuthorization = cancelYandexAuthorization
        self.observeYandexSession = observeYandexSession
        self.observeYandexAuthStatus = observeYandexAuthStatus
    }

    public func start() {
        guard observationTask == nil else { return }

        observationTask = Task { [weak self] in
            guard let self else { return }

            await withTaskGroup(of: Void.self) { group in
                group.addTask { [weak self] in
                    guard let self else { return }
                    for await session in observeYandexSession() {
                        if Task.isCancelled {
                            break
                        }
                        await self.handleSession(session)
                    }
                }

                group.addTask { [weak self] in
                    guard let self else { return }
                    for await status in observeYandexAuthStatus() {
                        if Task.isCancelled {
                            break
                        }
                        await self.handleAuthStatus(status)
                    }
                }
            }
        }
    }

    public func effectStream() -> AsyncStream<YandexMusicAuthCardEffect> {
        effectEmitter.stream()
    }

    public func onLoginTapped() async {
        state.isLoading = true

        do {
            let request = try await startYandexAuthorization()
            isAwaitingAuthorizationCallback = true
            effectEmitter.yield(.presentAuthWebView(request))
        } catch let error as YandexAuthException {
            state.isLoading = false
            isAwaitingAuthorizationCallback = false
            effectEmitter.yield(.showAlert(error.userMessage))
        } catch {
            state.isLoading = false
            isAwaitingAuthorizationCallback = false
            effectEmitter.yield(.showAlert(error.localizedDescription))
        }
    }

    public func onAuthWebViewLaunchFailed() async {
        state.isLoading = false
        isAwaitingAuthorizationCallback = false
        await cancelYandexAuthorization()
        effectEmitter.yield(.showAlert("Не удалось открыть страницу авторизации Яндекса."))
    }

    public func onAuthorizationCallback(_ callbackURL: URL) async {
        guard isAwaitingAuthorizationCallback else {
            return
        }

        isAwaitingAuthorizationCallback = false
        _ = try? await completeYandexAuthorization(callbackURL: callbackURL)
    }

    public func onAuthorizationDismissedWithoutCallback() async {
        guard isAwaitingAuthorizationCallback else {
            return
        }

        isAwaitingAuthorizationCallback = false
        await cancelYandexAuthorization()
    }

    private func handleSession(_ session: YandexAuthSession?) {
        state.isAuthorized = session != nil
    }

    private func handleAuthStatus(_ status: YandexAuthStatus) {
        switch status {
        case .unauthorized:
            state.isLoading = false
            isAwaitingAuthorizationCallback = false

        case .authorizing:
            state.isLoading = true

        case .authorized:
            state.isLoading = false
            state.isAuthorized = true
            isAwaitingAuthorizationCallback = false

        case let .failed(error):
            state.isLoading = false
            isAwaitingAuthorizationCallback = false
            effectEmitter.yield(.showAlert(error.userMessage))
        }
    }
}

private extension YandexAuthException {
    var userMessage: String {
        switch self {
        case .missingConfiguration:
            return "Не настроен Yandex OAuth. Проверьте client_id."

        case .invalidCallbackState:
            return "Сессия авторизации устарела. Попробуйте войти ещё раз."

        case .accessDenied:
            return "Доступ к аккаунту Яндекса не был подтверждён."

        default:
            return errorDescription ?? "Не удалось авторизоваться через Яндекс."
        }
    }
}
