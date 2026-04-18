package com.mplayeraudio.services.mediasession

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.player.PlayableSource
import com.mplayeraudio.core.player.PlayableUrlResolver
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.services.kithara.AudioEngineEvent
import com.mplayeraudio.services.kithara.AudioEngineState
import com.mplayeraudio.services.kithara.AudioEngineStatus
import com.mplayeraudio.services.kithara.AudioPlaybackEngine
import com.mplayeraudio.services.kithara.AudioTrackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Адаптер между Media3 [SimpleBasePlayer] и нашим [AudioPlaybackEngine].
 *
 * Состояние держится в [PlayerSnapshot]; конструирование `Player.State` вынесено
 * в [PlaybackStateBuilder], кэширование URL'ов — в [CachingPlayableUrlResolver].
 */
@OptIn(UnstableApi::class)
@Suppress("TooManyFunctions")
internal class KitharaSimplePlayer(
    private val context: Context,
    private val engine: AudioPlaybackEngine,
    private val urlResolver: PlayableUrlResolver,
    private val scope: CoroutineScope,
    private val stateBuilder: PlaybackStateBuilder = PlaybackStateBuilder(),
    private val invalidateUrlCache: (String) -> Unit = {},
    looper: Looper = Looper.myLooper() ?: Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {

    private val backgroundJobs = mutableListOf<Job>()
    private var snapshot = PlayerSnapshot()
    private var loadJob: Job? = null
    private var tickerJob: Job? = null

    init {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )

        backgroundJobs += scope.launch { syncEngineState() }
        backgroundJobs += scope.launch { handleEngineEvents() }
    }

    override fun getState(): State = stateBuilder.build(snapshot)

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<Any> {
        val queue = mediaItems.map(MediaItem::toQueueItem)
        val nextIndex = startIndex.takeIf { it in queue.indices }
        snapshot = snapshot.copy(
            queue = queue,
            currentIndex = nextIndex,
            playbackState = if (queue.isEmpty()) STATE_IDLE else STATE_READY,
            playerError = null,
        ).withPosition(startPositionMs)

        when {
            queue.isEmpty() -> resetEngine()
            nextIndex != null && snapshot.playWhenReady -> enqueue {
                playQueueItem(nextIndex, startPositionMs, playWhenReady = true)
            }
            else -> invalidateState()
        }

        return ImmediateVoid
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<Any> {
        snapshot = snapshot.copy(
            playWhenReady = playWhenReady,
            playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
        )

        when {
            !playWhenReady -> pauseEngine()
            snapshot.queue.isEmpty() -> Unit
            snapshot.currentIndex == null -> enqueue {
                playQueueItem(0, 0L, playWhenReady = true)
            }
            engine.engineState.value.currentItemId == snapshot.currentItem?.id -> resumeEngine()
            else -> enqueue {
                playQueueItem(
                    index = snapshot.currentIndex ?: 0,
                    startPositionMs = snapshot.contentPositionMs,
                    playWhenReady = true,
                )
            }
        }

        invalidateState()
        return ImmediateVoid
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<Any> {
        when (seekCommand) {
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> enqueue { playNextOrEnd() }

            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> enqueue { handlePreviousCommand() }

            else -> handleSeekToIndex(mediaItemIndex, positionMs)
        }
        return ImmediateVoid
    }

    override fun handlePrepare(): ListenableFuture<Any> {
        snapshot = snapshot.copy(
            playbackState = if (snapshot.queue.isEmpty()) STATE_IDLE else STATE_READY,
            playerError = null,
        )
        invalidateState()
        return ImmediateVoid
    }

    override fun handleRelease(): ListenableFuture<Any> {
        cancelLoadJob()
        tickerJob?.cancel()
        tickerJob = null
        backgroundJobs.forEach(Job::cancel)
        backgroundJobs.clear()
        engine.stop()
        return ImmediateVoid
    }

    override fun handleSetAudioAttributes(
        audioAttributes: AudioAttributes,
        handleAudioFocus: Boolean,
    ): ListenableFuture<Any> {
        snapshot = snapshot.copy(audioAttributes = audioAttributes)
        invalidateState()
        return ImmediateVoid
    }

    private fun resetEngine() {
        cancelLoadJob()
        engine.stop()
        updateTicker(isPlaying = false)
    }

    private fun pauseEngine() {
        engine.pause()
        snapshot = snapshot.copy(isPlaying = false)
        updateTicker(isPlaying = false)
    }

    private fun resumeEngine() {
        engine.play()
        snapshot = snapshot.copy(isPlaying = true, playbackState = STATE_READY)
        updateTicker(isPlaying = true)
    }

    private fun handleSeekToIndex(mediaItemIndex: Int, positionMs: Long) {
        val targetIndex = mediaItemIndex.takeIf { it in snapshot.queue.indices }
            ?: snapshot.currentIndex
            ?: return
        val clampedPositionMs = positionMs.coerceAtLeast(0L)
        val targetItemId = snapshot.queue[targetIndex].id
        val needsReload = targetIndex != snapshot.currentIndex ||
            engine.engineState.value.currentItemId != targetItemId

        enqueue {
            if (needsReload) {
                playQueueItem(targetIndex, clampedPositionMs, snapshot.playWhenReady)
            } else {
                seekCurrentItem(clampedPositionMs)
            }
        }
    }

    private suspend fun syncEngineState() {
        engine.engineState.collect { engineState ->
            applyEngineState(engineState)
            updateTicker(isPlaying = snapshot.isPlaying)
            invalidateState()
        }
    }

    private fun applyEngineState(engineState: AudioEngineState) {
        if (snapshot.queue.isEmpty()) {
            snapshot = snapshot.copy(playbackState = STATE_IDLE, isPlaying = false)
            return
        }

        val currentItemId = snapshot.currentItem?.id
        val isForCurrentItem = engineState.currentItemId != null &&
            engineState.currentItemId == currentItemId
        if (!isForCurrentItem) return

        snapshot = snapshot
            .withPosition(engineState.currentPositionMs)
            .copy(
                isPlaying = engineState.isPlaying,
                playbackState = reducePlaybackState(engineState),
            )
    }

    private fun reducePlaybackState(engineState: AudioEngineState): Int {
        return when {
            snapshot.queue.isEmpty() -> STATE_IDLE
            engineState.status == AudioEngineStatus.ReadyToPlay -> STATE_READY
            else -> snapshot.playbackState
        }
    }

    private suspend fun handleEngineEvents() {
        engine.events.collect { event ->
            when (event) {
                is AudioEngineEvent.CurrentItemChanged -> Unit
                is AudioEngineEvent.PlayedToEnd -> playNextOrEnd()
                is AudioEngineEvent.ItemFailed -> handleItemFailed(event)
            }
        }
    }

    private fun enqueue(block: suspend () -> Unit) {
        cancelLoadJob()
        loadJob = scope.launch { block() }
    }

    private fun cancelLoadJob() {
        loadJob?.cancel()
        loadJob = null
    }

    private suspend fun playQueueItem(
        index: Int,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        val item = snapshot.queue.getOrNull(index) ?: return

        val durationCap = item.durationMs.coerceAtLeast(0L)
        snapshot = snapshot.copy(
            currentIndex = index,
            playWhenReady = playWhenReady,
            playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            isPlaying = playWhenReady,
            playbackState = STATE_BUFFERING,
            playerError = null,
        ).withPosition(startPositionMs.coerceIn(0L, durationCap))

        invalidateState()
        loadAndStartItem(item, startPositionMs, playWhenReady)
    }

    private suspend fun loadAndStartItem(
        item: PlaybackQueueItem,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        try {
            val url = urlResolver.getPlayableUrl(item)
            engine.loadTrack(AudioTrackRequest(id = item.id, url = url))
            if (startPositionMs > 0L) {
                engine.seekTo(startPositionMs)
            }
            if (!playWhenReady) {
                engine.pause()
            }
        } catch (error: MusicLibraryException) {
            handleLoadFailure(item, error)
        } catch (error: IllegalStateException) {
            handleLoadFailure(item, error)
        }
    }

    private suspend fun seekCurrentItem(positionMs: Long) {
        val currentItem = snapshot.currentItem ?: return
        val clampedPosition = positionMs.coerceIn(0L, currentItem.durationMs.coerceAtLeast(0L))
        engine.seekTo(clampedPosition)
        snapshot = snapshot.withPosition(clampedPosition)
        invalidateState()
    }

    private suspend fun playNextOrEnd() {
        val currentIndex = snapshot.currentIndex ?: return
        val nextIndex = currentIndex + 1
        if (nextIndex > snapshot.queue.lastIndex) {
            markQueueFinished()
            return
        }
        playQueueItem(nextIndex, startPositionMs = 0L, playWhenReady = true)
    }

    private fun markQueueFinished() {
        snapshot = snapshot.copy(
            isPlaying = false,
            playWhenReady = false,
            playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM,
            playbackState = STATE_ENDED,
        ).withPosition(0L)
        updateTicker(isPlaying = false)
        invalidateState()
    }

    private suspend fun handlePreviousCommand() {
        val currentIndex = snapshot.currentIndex ?: return
        if (snapshot.contentPositionMs > RestartThresholdMs || currentIndex == 0) {
            seekCurrentItem(0L)
            return
        }
        playQueueItem(currentIndex - 1, startPositionMs = 0L, playWhenReady = true)
    }

    private suspend fun handleItemFailed(event: AudioEngineEvent.ItemFailed) {
        Log.e(TAG, "Item failed: ${event.itemId}, reason: ${event.reason}")
        val currentItem = snapshot.currentItem ?: return
        if (event.itemId != currentItem.id) return

        when (currentItem.source) {
            is PlayableSource.Local -> playNextOrEnd()
            is PlayableSource.Remote -> retryRemoteCurrentItem(currentItem)
        }
    }

    private suspend fun retryRemoteCurrentItem(item: PlaybackQueueItem) {
        invalidateUrlCache(item.id)
        try {
            val url = urlResolver.getPlayableUrl(item)
            engine.loadTrack(AudioTrackRequest(id = item.id, url = url))
        } catch (error: MusicLibraryException) {
            handleLoadFailure(item, error)
            playNextOrEnd()
        } catch (error: IllegalStateException) {
            handleLoadFailure(item, error)
            playNextOrEnd()
        }
    }

    private fun handleLoadFailure(item: PlaybackQueueItem, error: Exception) {
        Log.e(TAG, "Failed to load track ${item.trackId}", error)
        val message = error.message?.takeUnless(String::isBlank)
            ?: context.getString(R.string.media_session_load_failure)
        snapshot = snapshot.copy(
            isPlaying = false,
            playWhenReady = false,
            playWhenReadyChangeReason = PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            playbackState = STATE_IDLE,
            playerError = PlaybackException(
                message,
                error,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            ),
        ).withPosition(0L)
        updateTicker(isPlaying = false)
        invalidateState()
    }

    private fun updateTicker(isPlaying: Boolean) {
        if (!isPlaying) {
            tickerJob?.cancel()
            tickerJob = null
            return
        }
        if (tickerJob?.isActive == true) return

        tickerJob = scope.launch {
            while (true) {
                delay(PositionUpdateIntervalMs)
                invalidateState()
            }
        }
    }

    private companion object {
        const val TAG = "KitharaSimplePlayer"

        val ImmediateVoid: ListenableFuture<Any> = Futures.immediateFuture(Unit)
    }
}
