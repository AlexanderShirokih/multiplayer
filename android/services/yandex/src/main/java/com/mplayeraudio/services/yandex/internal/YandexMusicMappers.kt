package com.mplayeraudio.services.yandex.internal

import com.mplayeraudio.core.domain.musiclibrary.AlbumId
import com.mplayeraudio.core.domain.musiclibrary.ArtistPreview
import com.mplayeraudio.core.domain.musiclibrary.MusicLibraryException
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.MusicServiceAvailability
import com.mplayeraudio.core.domain.musiclibrary.Playlist
import com.mplayeraudio.core.domain.musiclibrary.PlaylistId
import com.mplayeraudio.core.domain.musiclibrary.PlaylistKind
import com.mplayeraudio.core.domain.musiclibrary.PlaylistSummary
import com.mplayeraudio.core.domain.musiclibrary.PlaylistTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.PlaylistUuid
import com.mplayeraudio.core.domain.musiclibrary.PlaylistRole
import com.mplayeraudio.core.domain.musiclibrary.PlaylistVisibility
import com.mplayeraudio.core.domain.musiclibrary.ProviderUserId
import com.mplayeraudio.core.domain.musiclibrary.SavedTrackEntry
import com.mplayeraudio.core.domain.musiclibrary.SavedTracks
import com.mplayeraudio.core.domain.musiclibrary.SavedTracksResult
import com.mplayeraudio.core.domain.musiclibrary.Track
import com.mplayeraudio.core.domain.musiclibrary.TrackId
import com.mplayeraudio.core.domain.musiclibrary.TrackPreview
import com.mplayeraudio.core.domain.musiclibrary.TrackRef
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal fun JsonObject.toAvailability(): MusicServiceAvailability {
    val account = requiredObject("account")
    val permissions = optionalObject("permissions")
    return MusicServiceAvailability(
        isAvailable = account.requiredBoolean("serviceAvailable"),
        region = account.optionalLong("region")?.toInt(),
        permissions = permissions?.optionalArray("values")
            .orEmpty()
            .mapNotNull(JsonElement::stringContent)
            .toSet(),
    )
}

internal fun JsonObject.toCurrentUserId(): ProviderUserId {
    val id = optionalString("id") ?: optionalString("default_uid")
    return id?.let(::ProviderUserId)
        ?: throw MusicLibraryException.InvalidResponse(
            description = "Yandex user info response does not contain user id.",
        )
}

internal fun JsonObject.toPlaylistSummary(): PlaylistSummary {
    val owner = requiredObject("owner")
    val ownerId = owner.optionalLong("uid")?.toString() ?: requiredLong("uid").toString()
    val kind = requiredLong("kind")
    return PlaylistSummary(
        id = PlaylistId(
            ownerId = ProviderUserId(ownerId),
            kind = PlaylistKind(kind),
        ),
        provider = MusicProviderId.YandexMusic,
        playlistUuid = optionalString("playlistUuid")?.let(::PlaylistUuid),
        title = requiredString("title"),
        ownerName = owner.optionalString("name"),
        coverUriTemplate = coverUriTemplate(),
        trackCount = requiredLong("trackCount").toInt(),
        durationMs = optionalLong("durationMs"),
        isAvailable = optionalBoolean("available") ?: true,
        isCollective = optionalBoolean("collective") ?: false,
        visibility = optionalString("visibility")?.toPlaylistVisibility(),
        role = if (kind == YandexFavouritesPlaylistKind) PlaylistRole.Favourites else PlaylistRole.Regular,
    )
}

private const val YandexFavouritesPlaylistKind = 3L

internal fun JsonObject.toPlaylist(): Playlist {
    val tracks = optionalArray("tracks")
        .orEmpty()
        .mapIndexed { index, element -> element.jsonObject.toPlaylistTrackEntry(index) }

    return Playlist(
        summary = toPlaylistSummary(),
        revision = optionalLong("revision"),
        snapshot = optionalLong("snapshot"),
        likesCount = optionalLong("likesCount")?.toInt(),
        tracks = tracks,
    )
}

internal fun JsonElement.toSavedTracksResult(): SavedTracksResult {
    val sentinel = stringContent()
    if (sentinel == "private-library") {
        return SavedTracksResult.PrivateLibrary
    }

    if (this is JsonPrimitive) {
        throw MusicLibraryException.InvalidResponse(
            description = "Unexpected saved tracks sentinel '$content'.",
        )
    }

    val root = jsonObject
    val library = root.optionalObject("library") ?: root
    return SavedTracksResult.Available(value = library.toSavedTracks())
}

internal fun TrackRef.toApiTrackId(): String {
    return albumId?.let { "${trackId.value}:${it.value}" } ?: trackId.value
}

private fun JsonObject.toPlaylistTrackEntry(position: Int): PlaylistTrackEntry {
    val track = optionalObject("track")?.toTrack()
    val trackId = optionalLong("id")?.toString()
        ?: track?.preview?.ref?.trackId?.value
        ?: throw MusicLibraryException.InvalidResponse(
            description = "Playlist track entry does not contain track id.",
        )

    return PlaylistTrackEntry(
        position = position,
        addedAt = optionalString("timestamp"),
        originalIndex = optionalLong("originalIndex")?.toInt(),
        originalShuffleIndex = optionalLong("originalShuffleIndex")?.toInt(),
        isRecent = optionalBoolean("recent"),
        trackRef = TrackRef(
            trackId = TrackId(trackId),
            albumId = track?.preview?.ref?.albumId,
        ),
        track = track,
    )
}

private fun JsonObject.toSavedTracks(): SavedTracks {
    val ownerId = optionalLong("uid")?.toString()
        ?: throw MusicLibraryException.InvalidResponse(
            description = "Saved tracks response does not contain uid.",
        )

    val revision = optionalLong("revision") ?: optionalLong("revisions")
    val tracks = optionalArray("tracks")
        .orEmpty()
        .mapIndexed { index, element -> element.jsonObject.toSavedTrackEntry(index) }

    return SavedTracks(
        ownerId = ProviderUserId(ownerId),
        revision = revision,
        tracks = tracks,
    )
}

private fun JsonObject.toSavedTrackEntry(position: Int): SavedTrackEntry {
    return SavedTrackEntry(
        position = position,
        addedAt = optionalString("timestamp"),
        trackRef = TrackRef(
            trackId = TrackId(requiredStringOrLong("id")),
            albumId = optionalStringOrLong("albumId")?.let(::AlbumId),
        ),
        track = optionalObject("track")?.toTrack()?.preview,
    )
}

internal fun JsonObject.toTrack(): Track {
    val trackId = optionalString("id") ?: optionalLong("id")?.toString()
    val ref = TrackRef(
        trackId = trackId?.let(::TrackId)
            ?: throw MusicLibraryException.InvalidResponse(
                description = "Track response does not contain id.",
            ),
        albumId = optionalArray("albums")
            ?.firstOrNull()
            ?.jsonObject
            ?.optionalLong("id")
            ?.toString()
            ?.let(::AlbumId),
    )

    val preview = TrackPreview(
        ref = ref,
        title = requiredString("title"),
        artists = optionalArray("artists")
            .orEmpty()
            .mapNotNull { artist -> artist.jsonObject.optionalArtistPreview() },
        durationMs = optionalLong("durationMs"),
        coverUriTemplate = optionalString("coverUri") ?: optionalString("ogImage"),
        isAvailable = optionalBoolean("available") ?: true,
    )

    return Track(
        preview = preview,
        lyricsAvailable = optionalBoolean("lyricsAvailable") ?: false,
        isAvailableForPremium = optionalBoolean("availableForPremiumUsers") ?: false,
        isAvailableWithoutPermission = optionalBoolean("availableFullWithoutPermission") ?: false,
    )
}

private fun JsonObject.optionalArtistPreview(): ArtistPreview? {
    val id = optionalString("id") ?: optionalLong("id")?.toString()
    val name = optionalString("name")
    return if (id != null && name != null) {
        ArtistPreview(
            id = id,
            name = name,
        )
    } else {
        null
    }
}
