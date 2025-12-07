package com.mamstricks.readsms

import android.app.Application

class ReadSmsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
    }
}
