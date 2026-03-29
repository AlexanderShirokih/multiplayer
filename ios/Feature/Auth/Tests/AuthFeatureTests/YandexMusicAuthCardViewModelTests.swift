@testable import AuthFeature
import Foundation
import XCTest

@MainActor
final class YandexMusicAuthCardViewModelTests: XCTestCase {
    func testOnLoginTappedEmitsPresentAuthWebViewEffect() async throws {
        let repository = FakeAuthRepository()
        let viewModel = makeViewModel(repository: repository)
        viewModel.start()

        let effectTask = Task {
            var iterator = viewModel.effectStream().makeAsyncIterator()
            return await iterator.next()
        }

        await viewModel.onLoginTapped()
        let effect = await effectTask.value

        XCTAssertEqual(
            effect,
            .presentAuthWebView(
                YandexAuthorizationRequest(
                    url: URL(string: "https://oauth.yandex.ru/authorize")!,
                    callbackURLPrefix: "https://music.yandex.ru/"
                )
            )
        )
        XCTAssertTrue(viewModel.state.isLoading)
    }

    func testFailedStatusEmitsAlertAndStopsLoading() async throws {
        let repository = FakeAuthRepository()
        let viewModel = makeViewModel(repository: repository)
        viewModel.start()

        let effectTask = Task {
            var iterator = viewModel.effectStream().makeAsyncIterator()
            return await iterator.next()
        }

        repository.statusRelay.yield(.failed(.providerError(code: "oauth_failed", description: "OAuth failed")))
        let effect = await effectTask.value

        XCTAssertEqual(effect, .showAlert("OAuth failed"))
        XCTAssertFalse(viewModel.state.isLoading)
    }

    func testAuthorizedSessionMarksUserAsAuthorized() async {
        let repository = FakeAuthRepository()
        let viewModel = makeViewModel(repository: repository)
        viewModel.start()

        let session = YandexAuthSession(
            accessToken: YandexAccessToken(rawValue: "token"),
            refreshToken: nil,
            tokenType: "bearer",
            expiresAt: nil,
            scopes: [],
            deviceId: YandexDeviceId(rawValue: "device"),
            user: YandexUserIdentity(
                id: YandexUserId(rawValue: "1"),
                login: "user",
                displayName: "User",
                email: nil,
                avatarId: nil
            ),
            clientId: YandexClientId(rawValue: "client")
        )

        repository.sessionRelay.yield(session)
        repository.statusRelay.yield(.authorized(session))
        await assertEventuallyTrue(viewModel.state.isAuthorized)

        XCTAssertTrue(viewModel.state.isAuthorized)
        XCTAssertFalse(viewModel.state.isLoading)
    }

    private func makeViewModel(repository: FakeAuthRepository) -> YandexMusicAuthCardViewModel {
        YandexMusicAuthCardViewModel(
            startYandexAuthorization: StartYandexAuthorizationUseCase(repository: repository),
            completeYandexAuthorization: CompleteYandexAuthorizationUseCase(repository: repository),
            cancelYandexAuthorization: CancelYandexAuthorizationUseCase(repository: repository),
            observeYandexSession: ObserveYandexSessionUseCase(repository: repository),
            observeYandexAuthStatus: ObserveYandexAuthStatusUseCase(repository: repository)
        )
    }

    func testAuthorizationCallbackCompletesAuthorization() async {
        let repository = FakeAuthRepository()
        let viewModel = makeViewModel(repository: repository)
        viewModel.start()

        await viewModel.onLoginTapped()
        let callbackURL = URL(string: "https://music.yandex.ru/#access_token=token-123&state=state-123")!
        await viewModel.onAuthorizationCallback(callbackURL)

        XCTAssertEqual(repository.completedCallbackURL, callbackURL)
    }

    func testDismissWithoutCallbackCancelsLoadingState() async {
        let repository = FakeAuthRepository()
        let viewModel = makeViewModel(repository: repository)
        viewModel.start()

        await viewModel.onLoginTapped()
        await viewModel.onAuthorizationDismissedWithoutCallback()

        await assertEventuallyFalse(viewModel.state.isLoading)
        XCTAssertFalse(viewModel.state.isLoading)
        XCTAssertEqual(repository.cancelCalls, 1)
    }
}

@MainActor
private func assertEventuallyTrue(
    _ value: @autoclosure () -> Bool,
    timeoutNanoseconds: UInt64 = 1_000_000_000
) async {
    let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
    while !value() && DispatchTime.now().uptimeNanoseconds < deadline {
        await Task.yield()
    }
}

@MainActor
private func assertEventuallyFalse(
    _ value: @autoclosure () -> Bool,
    timeoutNanoseconds: UInt64 = 1_000_000_000
) async {
    let deadline = DispatchTime.now().uptimeNanoseconds + timeoutNanoseconds
    while value() && DispatchTime.now().uptimeNanoseconds < deadline {
        await Task.yield()
    }
}

private final class FakeAuthRepository: YandexAuthRepository, @unchecked Sendable {
    let sessionRelay = AsyncValueRelay<YandexAuthSession?>(nil)
    let statusRelay = AsyncValueRelay<YandexAuthStatus>(.unauthorized)
    private(set) var cancelCalls = 0
    private(set) var completedCallbackURL: URL?

    func currentSession() -> YandexAuthSession? {
        sessionRelay.currentValue
    }

    func observeSession() -> AsyncStream<YandexAuthSession?> {
        sessionRelay.stream()
    }

    func observeStatus() -> AsyncStream<YandexAuthStatus> {
        statusRelay.stream()
    }

    func createAuthorizationRequest() async throws -> YandexAuthorizationRequest {
        statusRelay.yield(.authorizing)
        return YandexAuthorizationRequest(
            url: URL(string: "https://oauth.yandex.ru/authorize")!,
            callbackURLPrefix: "https://music.yandex.ru/"
        )
    }

    func completeAuthorization(callbackURL: URL) async throws -> YandexAuthSession {
        completedCallbackURL = callbackURL
        throw YandexAuthException.providerError(
            code: "missing_access_token",
            description: "Yandex OAuth callback does not contain an access token."
        )
    }

    func cancelAuthorization() async {
        cancelCalls += 1
        statusRelay.yield(.unauthorized)
    }

    func logout() async {
        sessionRelay.yield(nil)
        statusRelay.yield(.unauthorized)
    }
}
