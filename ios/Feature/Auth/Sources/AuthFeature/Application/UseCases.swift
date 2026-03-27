import Foundation

public struct StartYandexAuthorizationUseCase: Sendable {
    private let repository: YandexAuthRepository

    public init(repository: YandexAuthRepository) {
        self.repository = repository
    }

    public func callAsFunction() async throws -> YandexAuthorizationRequest {
        try await repository.createAuthorizationRequest()
    }
}

public struct CompleteYandexAuthorizationUseCase: Sendable {
    private let repository: YandexAuthRepository

    public init(repository: YandexAuthRepository) {
        self.repository = repository
    }

    public func callAsFunction(callbackURL: URL) async throws -> YandexAuthSession {
        try await repository.completeAuthorization(callbackURL: callbackURL)
    }
}

public struct CancelYandexAuthorizationUseCase: Sendable {
    private let repository: YandexAuthRepository

    public init(repository: YandexAuthRepository) {
        self.repository = repository
    }

    public func callAsFunction() async {
        await repository.cancelAuthorization()
    }
}

public struct ObserveYandexSessionUseCase: Sendable {
    private let repository: YandexAuthRepository

    public init(repository: YandexAuthRepository) {
        self.repository = repository
    }

    public func callAsFunction() -> AsyncStream<YandexAuthSession?> {
        repository.observeSession()
    }
}

public struct ObserveYandexAuthStatusUseCase: Sendable {
    private let repository: YandexAuthRepository

    public init(repository: YandexAuthRepository) {
        self.repository = repository
    }

    public func callAsFunction() -> AsyncStream<YandexAuthStatus> {
        repository.observeStatus()
    }
}

public struct ObserveAuthorizedMusicProviderUseCase: Sendable {
    private let repository: MusicProviderAuthorizationRepository

    public init(repository: MusicProviderAuthorizationRepository) {
        self.repository = repository
    }

    public func currentAuthorizedProvider() -> AuthorizedMusicProvider? {
        repository.currentAuthorizedProvider()
    }

    public func callAsFunction() -> AsyncStream<AuthorizedMusicProvider?> {
        repository.observeAuthorizedProvider()
    }
}
