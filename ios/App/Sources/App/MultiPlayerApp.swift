import CoreUI
import SwiftUI

@main
struct MultiPlayerApp: App {
    @State private var appRoot = AppRoot()

    var body: some Scene {
        WindowGroup {
            MultiplayerDesignSystem {
                RootView()
                    .environment(appRoot)
            }
        }
    }
}
