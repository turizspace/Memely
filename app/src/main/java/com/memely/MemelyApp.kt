package com.memely

import android.app.Application
import com.memely.util.SecureStorage

class MemelyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SecureStorage.init(this)
    }
}