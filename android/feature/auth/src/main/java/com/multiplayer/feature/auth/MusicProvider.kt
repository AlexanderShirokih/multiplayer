package com.multiplayer.feature.auth

data class MusicProvider(
    val type: String,
) {
    companion object {
        val YandexMusic = MusicProvider(type = "yandex_music")
    }
}
