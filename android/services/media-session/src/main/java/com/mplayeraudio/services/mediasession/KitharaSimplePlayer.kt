package com.mplayeraudio.services.mediasession

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mplayeraudio.core.player.PlaybackError
import com.mplayeraudio.core.player.PlaybackPhase
import com.mplayeraudio.core.player.PlaybackQueueBridge
import com.mplayeraudio.core.player.PlaybackQueueItem
import com.mplayeraudio.core.player.PlaybackQueueState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Тонкий адаптер между Media3 [SimpleBasePlayer] и [PlaybackQueueBridge].
 *
 * Не хранит собственного состояния — весь state живёт в [PlaybackQueueBridge].
 * Задача: синхронизировать Media3-сессию (уведомление, lock-screen, Bluetooth)
 * с канонным состоянием контроллера, и делегировать входящие Media3-команды
 * обратно в контроллер.
 */
@OptIn(UnstableApi::class)
@Suppress("TooManyFunctions")
internal class KitharaSimplePlayer(
    private val controller: PlaybackQueueBridge,
    private val scope: CoroutineScope,
    looper: Looper = Looper.myLooper() ?: Looper.getMainLooper(),
) : SimpleBasePlayer(looper) {

    private var currentState = PlaybackQueueState()
    private var collectJob: Job? = null

    init {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        collectJob = scope.launch {
            controller.playbackState.collect { state ->
                currentState = state
                invalidateState()
            }
        }
    }

    override fun getState(): State = currentState.toPlayer3State()

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<Any> {
        val queue = mediaItems.map(MediaItem::toQueueItem)
        val nextIndex = startIndex.takeIf { it in queue.indices }
        scope.launch {
            controller.replaceQueue(queue = queue, startIndex = nextIndex, autoPlay = false)
        }
        return ImmediateVoid
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<Any> {
        scope.launch {
            if (playWhenReady) controller.play() else controller.pause()
        }
        return ImmediateVoid
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<Any> {
        scope.launch {
            when (seekCommand) {
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> controller.skipNext()

                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> controller.skipPrevious()

                else -> controller.seekTo(positionMs)
            }
        }
        return ImmediateVoid
    }

    override fun handlePrepare(): ListenableFuture<Any> = ImmediateVoid

    override fun handleRelease(): ListenableFuture<Any> {
        collectJob?.cancel()
        collectJob = null
        return ImmediateVoid
    }

    override fun handleSetAudioAttributes(
        audioAttributes: AudioAttributes,
        handleAudioFocus: Boolean,
    ): ListenableFuture<Any> = ImmediateVoid

    private companion object {
        val ImmediateVoid: ListenableFuture<Any> = Futures.immediateFuture(Unit)
    }
}

@OptIn(UnstableApi::class)
private fun PlaybackQueueState.toPlayer3State(): SimpleBasePlayer.State {
    val (player3PlaybackState, playWhenReady) = when (phase) {
        PlaybackPhase.Idle -> Player.STATE_IDLE to false
        PlaybackPhase.Loading, PlaybackPhase.Buffering -> Player.STATE_BUFFERING to true
        PlaybackPhase.Playing -> Player.STATE_READY to true
        PlaybackPhase.Paused -> Player.STATE_READY to false
        PlaybackPhase.Failed -> Player.STATE_IDLE to false
        PlaybackPhase.Ended -> Player.STATE_ENDED to false
    }

    val playerError = if (phase == PlaybackPhase.Failed) {
        PlaybackException(
            playbackError.toErrorMessage(),
            null,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
    } else {
        null
    }

    return SimpleBasePlayer.State.Builder()
        .setAvailableCommands(defaultAvailableCommands())
        .setPlaylist(buildPlaylist())
        .setCurrentMediaItemIndex(currentIndex ?: C.INDEX_UNSET)
        .setPlaybackState(player3PlaybackState)
        .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setContentPositionMs { currentPositionMs }
        .setContentBufferedPositionMs { bufferedPositionMs }
        .setMaxSeekToPreviousPositionMs(RestartThresholdMs)
        .setPlayerError(playerError)
        .build()
}

@OptIn(UnstableApi::class)
private fun PlaybackQueueState.buildPlaylist(): List<SimpleBasePlayer.MediaItemData> {
    return queue.mapIndexed { index, item ->
        val effectiveDuration = if (index == currentIndex) {
            currentDurationMs ?: item.durationMs
        } else {
            item.durationMs
        }
        SimpleBasePlayer.MediaItemData.Builder(item.id)
            .setMediaItem(item.toMediaItem())
            .setDurationUs(effectiveDuration * MicrosecondsPerMillisecond)
            .build()
    }
}

private fun PlaybackError?.toErrorMessage(): String {
    return when (this) {
        is PlaybackError.TrackUnavailable -> message
        is PlaybackError.StreamFailed -> message
        is PlaybackError.EngineCrashed -> message
        null -> "Playback error"
    }
}

@OptIn(UnstableApi::class)
@Suppress("MagicNumber")
private fun defaultAvailableCommands(): Player.Commands {
    return Player.Commands.Builder()
        .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
        .add(Player.COMMAND_GET_METADATA)
        .add(Player.COMMAND_GET_TEXT)
        .add(Player.COMMAND_PLAY_PAUSE)
        .add(Player.COMMAND_PREPARE)
        .add(Player.COMMAND_RELEASE)
        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
        .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_NEXT)
        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        .add(Player.COMMAND_SET_AUDIO_ATTRIBUTES)
        .build()
}

