package com.mplayeraudio.services.mediasession

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

const val MediaPlaybackNotificationChannelId = "media_playback_channel"

internal const val MediaItemDurationMsKey = "duration_ms"
internal const val MediaItemTrackIdTypeKey = "track_id_type"
internal const val MediaItemTrackIdStringKey = "track_id_string"
internal const val MediaItemTrackIdLongKey = "track_id_long"
internal const val MediaItemSourceTypeKey = "source_type"
internal const val MediaItemSourceLocalUriKey = "source_local_uri"
internal const val MediaItemSourceRemoteProviderKey = "source_remote_provider"

internal const val MediaItemTrackIdTypeYandex = "yandex"
internal const val MediaItemTrackIdTypeDevice = "device"
internal const val MediaItemTrackIdTypeUserPlaylist = "user_playlist"

internal const val MediaItemSourceTypeLocal = "local"
internal const val MediaItemSourceTypeRemote = "remote"

internal val PositionUpdateInterval = 500.milliseconds
internal val RestartThreshold = 3_000.milliseconds
internal val StreamUrlTtl = 25.minutes

internal val PositionUpdateIntervalMs: Long = PositionUpdateInterval.inWholeMilliseconds
internal val RestartThresholdMs: Long = RestartThreshold.inWholeMilliseconds
internal val StreamUrlTtlMs: Long = StreamUrlTtl.inWholeMilliseconds

internal const val MicrosecondsPerMillisecond = 1_000L
