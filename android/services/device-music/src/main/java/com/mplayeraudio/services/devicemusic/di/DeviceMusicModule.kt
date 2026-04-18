package com.mplayeraudio.services.devicemusic.di

import com.mplayeraudio.core.domain.musiclibrary.MusicProvider
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.services.devicemusic.DeviceMusicProvider
import com.mplayeraudio.services.devicemusic.DeviceTrackStreamUrlProvider
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun deviceMusicModule(): Module = module {
    single {
        DeviceMusicProvider(androidContext())
    } bind MusicProvider::class
    single<TrackStreamUrlProvider>(named(MusicProviderId.Device.name)) {
        DeviceTrackStreamUrlProvider(
            contentResolver = androidContext().contentResolver,
        )
    }
}
