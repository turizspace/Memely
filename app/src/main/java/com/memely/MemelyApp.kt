package com.memely

import android.app.Application
import com.memely.di.AppContainer
import com.memely.nostr.KeyStoreManager
import com.memely.ui.theme.ThemeManager
import com.memely.ui.tutorial.TutorialManager
import com.memely.util.SecureStorage
import com.memely.data.metadata.MetadataUpdateListener

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
        
        // Initialize metadata update listener to automatically refresh profiles
        // when new kind 0 events arrive from relays
        MetadataUpdateListener.startListening(appContainer.profileRepository)
    }
}
