import CorePlayer

public enum NowPlayingServiceModule {
    @MainActor
    public static func make(playbackBridge: PlaybackQueueBridge) -> NowPlayingCenter {
        NowPlayingCenter(
            playbackBridge: playbackBridge,
            artworkLoader: ArtworkLoader()
        )
    }
}
