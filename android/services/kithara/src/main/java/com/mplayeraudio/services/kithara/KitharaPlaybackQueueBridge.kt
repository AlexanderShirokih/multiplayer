package com.mplayeraudio.services.kithara

import android.util.Log
import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.core.player.NowPlayingStripExternalState
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
internal class KitharaPlaybackQueueBridge(
    private val engine: AudioPlaybackEngine,
    private val urlProvider: TrackStreamUrlProvider,
    scope: CoroutineScope,
) : PlaybackQueueBridge {

    private val stateFlow = MutableStateFlow(PlaybackQueueState())

    private val urlCache = mutableMapOf<String, CachedStreamUrl>()

    override val playbackState: Flow<PlaybackQueueState> = stateFlow.asStateFlow()

    override val state: Flow<NowPlayingStripExternalState> = playbackState.map { playbackState ->
        val currentItem = playbackState.currentItem
        NowPlayingStripExternalState(
            title = currentItem?.title.orEmpty(),
            subtitle = currentItem?.subtitle.orEmpty(),
            isPlaying = playbackState.isPlaying,
            currentPositionMs = playbackState.currentPositionMs,
            durationMs = currentItem?.durationMs ?: 0L,
            controlsEnabled = playbackState.controlsEnabled,
        )
    }

    init {
        scope.launch { syncEngineState() }
        scope.launch { handleEngineEvents() }
    }

    override suspend fun replaceQueue(
        queue: List<PlaybackQueueItem>,
        startIndex: Int?,
        autoPlay: Boolean,
    ) {
        val previousState = stateFlow.value
        val preservedIndex = previousState.currentItem
            ?.let { currentItem ->
                queue.indexOfFirst { queuedItem -> queuedItem.id == currentItem.id }
                    .takeIf { index -> index >= 0 }
            }
        val nextIndex = startIndex ?: preservedIndex
        val preservingCurrentTrack = nextIndex != null && nextIndex == preservedIndex

        val currentPositionMs = if (preservingCurrentTrack) {
            previousState.currentPositionMs.coerceIn(
                0L,
                queue[nextIndex].durationMs.coerceAtLeast(0L),
            )
        } else {
            0L
        }

        val shouldPlay = when {
            queue.isEmpty() -> false
            autoPlay && nextIndex != null -> true
            preservingCurrentTrack -> previousState.isPlaying
            else -> false
        }

        stateFlow.value = PlaybackQueueState(
            queue = queue,
            currentIndex = nextIndex,
            isPlaying = shouldPlay,
            currentPositionMs = currentPositionMs,
            controlsEnabled = queue.isNotEmpty(),
        )

        if (shouldPlay && !preservingCurrentTrack && nextIndex != null) {
            loadAndPlay(queue[nextIndex])
            return
        }

        if (!preservingCurrentTrack) {
            engine.stop()
        }
    }

    override suspend fun playTrack(index: Int) {
        val currentState = stateFlow.value
        if (index !in currentState.queue.indices) return

        val item = currentState.queue[index]
        stateFlow.value = currentState.copy(
            currentIndex = index,
            isPlaying = true,
            currentPositionMs = 0L,
            controlsEnabled = true,
        )
        loadAndPlay(item)
    }

    override suspend fun play() {
        val currentState = stateFlow.value
        if (currentState.queue.isEmpty()) return

        val index = currentState.currentIndex ?: 0
        val item = currentState.queue[index]
        val loadedItemId = engine.engineState.value.currentItemId

        if (currentState.currentIndex != null && loadedItemId == item.id) {
            engine.play()
            stateFlow.value = currentState.copy(
                isPlaying = true,
                controlsEnabled = true,
            )
        } else {
            stateFlow.value = currentState.copy(
                currentIndex = 0,
                isPlaying = true,
                controlsEnabled = true,
            )
            loadAndPlay(item)
        }
    }

    override suspend fun pause() {
        val currentState = stateFlow.value
        if (currentState.queue.isEmpty()) return

        engine.pause()
        stateFlow.value = currentState.copy(isPlaying = false)
    }

    override suspend fun skipNext() {
        val currentState = stateFlow.value
        val currentIndex = currentState.currentIndex ?: return
        val nextIndex = (currentIndex + 1).coerceAtMost(currentState.queue.lastIndex)

        if (nextIndex == currentIndex) return

        val item = currentState.queue[nextIndex]
        stateFlow.value = currentState.copy(
            currentIndex = nextIndex,
            currentPositionMs = 0L,
            isPlaying = true,
        )
        loadAndPlay(item)
    }

    override suspend fun skipPrevious() {
        val currentState = stateFlow.value
        val currentIndex = currentState.currentIndex ?: return

        val shouldRestartCurrentTrack = currentState.currentPositionMs > RestartThresholdMs ||
            currentIndex == currentState.queue.indices.first()
        if (shouldRestartCurrentTrack) {
            engine.seekTo(0L)
            stateFlow.value = currentState.copy(currentPositionMs = 0L)
            return
        }

        val previousIndex = currentIndex - 1
        val item = currentState.queue[previousIndex]
        stateFlow.value = currentState.copy(
            currentIndex = previousIndex,
            currentPositionMs = 0L,
            isPlaying = true,
        )
        loadAndPlay(item)
    }

    override suspend fun seekTo(positionMs: Long) {
        val currentState = stateFlow.value
        val currentItem = currentState.currentItem ?: return
        val clampedMs = positionMs.coerceIn(0L, currentItem.durationMs.coerceAtLeast(0L))

        engine.seekTo(clampedMs)
        stateFlow.value = stateFlow.value.copy(currentPositionMs = clampedMs)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadAndPlay(item: PlaybackQueueItem) {
        try {
            val url = resolveStreamUrl(item)
            engine.loadTrack(AudioTrackRequest(id = item.id, url = url))
        } catch (e: MusicLibraryException) {
            Log.e(Tag, "Failed to resolve stream URL for track ${item.trackId}", e)
            stateFlow.value = stateFlow.value.copy(
                isPlaying = false,
                currentPositionMs = 0L,
            )
        } catch (e: IllegalStateException) {
            Log.e(Tag, "Failed to resolve stream URL for track ${item.trackId}", e)
            stateFlow.value = stateFlow.value.copy(
                isPlaying = false,
                currentPositionMs = 0L,
            )
        }
    }

    private suspend fun resolveStreamUrl(item: PlaybackQueueItem): String {
        evictExpiredUrls()
        val cached = urlCache[item.id]
        if (cached != null && !cached.isExpired()) {
            return cached.url
        }

        val url = urlProvider.getStreamUrl(item.trackId)
        urlCache[item.id] = CachedStreamUrl(url = url, resolvedAt = System.currentTimeMillis())
        return url
    }

    private fun evictExpiredUrls() {
        val iterator = urlCache.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.isExpired()) {
                iterator.remove()
            }
        }
    }

    private suspend fun syncEngineState() {
        engine.engineState.collect { engineState ->
            stateFlow.value = stateFlow.value.copy(
                currentPositionMs = engineState.currentPositionMs,
                isPlaying = engineState.isPlaying,
            )
        }
    }

    private suspend fun handleEngineEvents() {
        engine.events.collect { event ->
            when (event) {
                is AudioEngineEvent.PlayedToEnd -> handlePlayedToEnd()
                is AudioEngineEvent.ItemFailed -> handleItemFailed(event)
                is AudioEngineEvent.CurrentItemChanged -> Unit
            }
        }
    }

    private suspend fun handlePlayedToEnd() {
        val currentState = stateFlow.value
        val currentIndex = currentState.currentIndex ?: return

        val nextIndex = currentIndex + 1
        if (nextIndex > currentState.queue.lastIndex) {
            stateFlow.value = currentState.copy(
                isPlaying = false,
                currentPositionMs = 0L,
            )
            return
        }

        val nextItem = currentState.queue[nextIndex]
        stateFlow.value = currentState.copy(
            currentIndex = nextIndex,
            currentPositionMs = 0L,
            isPlaying = true,
        )
        loadAndPlay(nextItem)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun handleItemFailed(event: AudioEngineEvent.ItemFailed) {
        Log.e(Tag, "Item failed: ${event.itemId}, reason: ${event.reason}")
        val currentState = stateFlow.value
        val currentItem = currentState.currentItem ?: return

        if (event.itemId == currentItem.id) {
            urlCache.remove(currentItem.id)
            try {
                val url = resolveStreamUrl(currentItem)
                engine.loadTrack(AudioTrackRequest(id = currentItem.id, url = url))
            } catch (e: MusicLibraryException) {
                Log.e(Tag, "Retry failed for track ${currentItem.trackId}", e)
                handlePlayedToEnd()
            } catch (e: IllegalStateException) {
                Log.e(Tag, "Retry failed for track ${currentItem.trackId}", e)
                handlePlayedToEnd()
            }
        }
    }
}

private data class CachedStreamUrl(
    val url: String,
    val resolvedAt: Long,
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - resolvedAt > UrlTtlMs
    }
}

private const val RestartThresholdMs = 3_000L
private const val UrlTtlMs = 25L * 60L * 1_000L
private const val Tag = "KitharaQueueBridge"
