package com.jepongdevxyz.devxyzfiles

import android.annotation.SuppressLint
import android.content.Context

/**
 * Application context holder so the Shizuku singleton can build its
 * ComponentName without an Activity reference. Set once in MainActivity.
 */
object AppContext {
    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun get(): Context {
        return context ?: throw IllegalStateException("AppContext not initialised")
    }
}
