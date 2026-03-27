import SwiftUI

struct RootView: View {
    @Environment(AppRoot.self) private var appRoot

    var body: some View {
        Group {
            switch appRoot.destination {
            case .auth:
                WelcomeView(
                    onConnectYandexMusic: {
                        appRoot.destination = .player
                    }
                )
                .transition(.opacity)
            case .player:
                PlayerPlaceholderView()
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut, value: appRoot.destination)
    }
}
