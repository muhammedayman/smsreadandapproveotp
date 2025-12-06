package com.example.readsms

import android.content.Context
import android.util.Log
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication

class ReadSmsApp : MultiDexApplication() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // androidx.appcompat.app.AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        
        // Setup global crash handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CRASH_REPORT", "FATAL EXCEPTION: " + throwable.message, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
