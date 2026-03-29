package com.mplayeraudio.services.yandex.internal.network

internal data class YandexMusicStreamingConfig(
    val downloadInfoSigningSecret: String,
    val downloadInfoClientHeader: String,
    val fileInfoSigningSecret: String,
    val fileInfoClientHeader: String,
) {
    val isDownloadInfoEnabled: Boolean
        get() = downloadInfoSigningSecret.isNotBlank()

    val isFileInfoEnabled: Boolean
        get() = fileInfoSigningSecret.isNotBlank()
}
