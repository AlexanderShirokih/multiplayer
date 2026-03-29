import CoreDomain
import CorePlayer
import Kithara

public enum ServicesKitharaPlayerModule {
    public static func makePlaybackQueueBridge(
        urlProvider: TrackStreamUrlProvider
    ) -> PlaybackQueueBridge {
        KitharaPlaybackQueueBridge(urlProvider: urlProvider)
    }
}
