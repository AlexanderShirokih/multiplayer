package com.mplayeraudio.services.mediasession

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.util.UnstableApi
import com.mplayeraudio.core.player.PlaybackQueueItem

@OptIn(UnstableApi::class)
internal class PlaybackStateBuilder(
    private val availableCommands: Player.Commands = defaultAvailableCommands(),
) {
    private var cachedQueueKey: List<PlaybackQueueItem>? = null
    private var cachedPlaylist: List<MediaItemData> = emptyList()

    fun build(snapshot: PlayerSnapshot): SimpleBasePlayer.State {
        return SimpleBasePlayer.State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaylist(playlistFor(snapshot.queue))
            .setCurrentMediaItemIndex(snapshot.currentIndex ?: C.INDEX_UNSET)
            .setPlaybackState(snapshot.playbackState)
            .setPlayWhenReady(snapshot.playWhenReady, snapshot.playWhenReadyChangeReason)
            .setContentPositionMs { snapshot.extrapolatedPositionMs() }
            .setContentBufferedPositionMs { snapshot.extrapolatedPositionMs() }
            .setAudioAttributes(snapshot.audioAttributes)
            .setMaxSeekToPreviousPositionMs(RestartThresholdMs)
            .setPlayerError(snapshot.playerError)
            .build()
    }

    private fun playlistFor(queue: List<PlaybackQueueItem>): List<MediaItemData> {
        if (cachedQueueKey === queue) {
            return cachedPlaylist
        }
        val rebuilt = queue.map { item ->
            MediaItemData.Builder(item.id)
                .setMediaItem(item.toMediaItem())
                .setDurationUs(item.durationMs * MicrosecondsPerMillisecond)
                .build()
        }
        cachedQueueKey = queue
        cachedPlaylist = rebuilt
        return rebuilt
    }
}

@OptIn(UnstableApi::class)
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
