import LibraryFeature
import SwiftUI

struct RootView: View {
    @Environment(AppRoot.self) private var appRoot

    var body: some View {
        Group {
            switch appRoot.destination {
            case .auth:
                WelcomeView(viewModel: appRoot.authCardViewModel)
                    .transition(.opacity)
            case .library:
                MusicLibraryView(
                    viewModel: appRoot.musicLibraryViewModel,
                    trackListViewModelFactory: appRoot.makeTrackListViewModel
                )
                .transition(.opacity)
            }
        }
        .task {
            appRoot.start()
        }
        .animation(.easeInOut, value: appRoot.destination)
    }
}
