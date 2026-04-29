package com.mplayeraudio.services.kithara

import android.util.Log

interface KitharaLogger {
    fun trace(tag: String, message: String)

    fun error(tag: String, message: String, throwable: Throwable? = null)
}

internal object NoOpKitharaLogger : KitharaLogger {
    override fun trace(tag: String, message: String) = Unit

    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
}

internal object AndroidKitharaLogger : KitharaLogger {
    override fun trace(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
