package com.mplayeraudio.services.mediasession.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val MediaSessionScopeQualifier = "mediaSessionScope"

fun mediaSessionModule(): Module = module {
    single<CoroutineScope>(named(MediaSessionScopeQualifier)) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
