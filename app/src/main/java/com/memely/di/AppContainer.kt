package com.memely.di

import android.app.Application
import android.content.Context
import com.memely.MemelyApp
import com.memely.blossom.BlossomClient
import com.memely.data.InteractionRepository
import com.memely.data.repositories.DefaultProfileRepository
import com.memely.data.repositories.DefaultSessionRepository
import com.memely.data.repositories.ProfileRepository
import com.memely.data.repositories.SessionRepository
import com.memely.data.repositories.ThemeRepository
import com.memely.nostr.DefaultInteractionPublisher
import com.memely.nostr.DefaultNostrNotePublisher
import com.memely.nostr.InteractionPublisher
import com.memely.nostr.NostrNotePublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(
    application: Application
) {
    private val appContext = application.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessionRepository: SessionRepository = DefaultSessionRepository(appScope)
    val profileRepository: ProfileRepository = DefaultProfileRepository()
    val themeRepository: ThemeRepository = ThemeRepository(appContext)
    val interactionRepository: InteractionRepository = InteractionRepository(appScope)
    val interactionPublisher: InteractionPublisher = DefaultInteractionPublisher()
    val blossomClient: BlossomClient = BlossomClient()
    val nostrNotePublisher: NostrNotePublisher = DefaultNostrNotePublisher()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MemelyApp).appContainer
