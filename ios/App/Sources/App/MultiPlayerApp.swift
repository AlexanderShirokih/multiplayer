import CoreUI
import SwiftUI

@main
struct MultiPlayerApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var appRoot = AppRoot()

    var body: some Scene {
        WindowGroup {
            MultiplayerDesignSystem {
                RootView()
                    .environment(appRoot)
                    .onOpenURL { url in
                        Task {
                            await appRoot.handleIncomingURL(url)
                        }
                    }
                    .onChange(of: scenePhase) { _, newPhase in
                        guard newPhase == .active else {
                            return
                        }

                        Task {
                            await appRoot.handleAppDidBecomeActive()
                        }
                    }
            }
        }
    }
}
