import Observation

@Observable
@MainActor
final class AppRoot {
    enum Destination: Hashable {
        case auth
        case player
    }

    var destination: Destination = .auth
}
