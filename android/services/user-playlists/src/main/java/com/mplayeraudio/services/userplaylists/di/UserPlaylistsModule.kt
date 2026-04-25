package com.mplayeraudio.services.userplaylists.di

import androidx.room.Room
import com.mplayeraudio.core.domain.musiclibrary.MusicProvider
import com.mplayeraudio.core.domain.musiclibrary.MusicProviderId
import com.mplayeraudio.core.domain.musiclibrary.UserPlaylistsRepository
import com.mplayeraudio.core.domain.musiclibrary.TrackStreamUrlProvider
import com.mplayeraudio.services.userplaylists.UrlMetadataExtractor
import com.mplayeraudio.services.userplaylists.UserPlaylistsProvider
import com.mplayeraudio.services.userplaylists.UserPlaylistsRepositoryImpl
import com.mplayeraudio.services.userplaylists.UserPlaylistsTrackStreamUrlProvider
import com.mplayeraudio.services.userplaylists.data.UserPlaylistsDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val userPlaylistsModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            UserPlaylistsDatabase::class.java,
            "user_playlists.db"
        ).build()
    }

    single { get<UserPlaylistsDatabase>().userPlaylistDao() }

    singleOf(::UrlMetadataExtractor)
    singleOf(::UserPlaylistsProvider) { bind<MusicProvider>() }
    singleOf(::UserPlaylistsRepositoryImpl) { bind<UserPlaylistsRepository>() }

    single<TrackStreamUrlProvider>(named(MusicProviderId.UserPlaylists.name)) {
        UserPlaylistsTrackStreamUrlProvider(get())
    }
}
