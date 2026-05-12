package com.memely

import android.app.Application
import com.memely.di.AppContainer
import com.memely.nostr.KeyStoreManager
import com.memely.ui.theme.ThemeManager
import com.memely.ui.tutorial.TutorialManager
import com.memely.util.SecureStorage

class MemelyApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        SecureStorage.init(this)
        KeyStoreManager.init(this)
        ThemeManager.initialize(this)
        TutorialManager.initialize(this)
        appContainer = AppContainer(this)
    }
}
