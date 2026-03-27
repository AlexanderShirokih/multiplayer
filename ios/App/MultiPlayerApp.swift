import CoreUI
import SwiftUI

@main
struct MultiPlayerApp: App {
    var body: some Scene {
        WindowGroup {
            MultiplayerDesignSystem {
                RootView()
            }
        }
    }
}
