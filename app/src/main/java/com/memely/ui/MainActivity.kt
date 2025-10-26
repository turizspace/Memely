package com.memely.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.memely.nostr.*
import com.memely.ui.components.BottomBar
import com.memely.ui.components.UserTopBar
import com.memely.ui.screens.*

class MainActivity : ComponentActivity() {

    private val amberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { AmberSignerManager.handleIntentResponse(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyStoreManager.init(applicationContext)

        // Restore Amber configuration if user logged in with Amber
        if (KeyStoreManager.isUsingAmber()) {
            val pubkey = KeyStoreManager.getPubkeyHex()
            val packageName = KeyStoreManager.getAmberPackageName()
            if (pubkey != null && packageName != null) {
                AmberSignerManager.configure(pubkey, packageName)
                android.util.Log.d("MemelyApp", "✅ Restored Amber config: pubkey=${pubkey.take(12)}..., package=$packageName")
            }
        }

        AmberSignerManager.registerActivityLauncher { intent ->
            amberLauncher.launch(intent)
        }

        // Handle initial intent (if app was launched via nostrsigner:// callback)
        handleAmberCallback(intent)

        setContent {
            AppRoot(openUrl = { amberLauncher.launch(it) })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle callback from Amber when returning to app
        handleAmberCallback(intent)
    }

    private fun handleAmberCallback(intent: Intent?) {
        if (intent?.scheme == "nostrsigner") {
            android.util.Log.d("MemelyApp", "📥 Received nostrsigner callback")
            android.util.Log.d("MemelyApp", "   URI: ${intent.data}")
            android.util.Log.d("MemelyApp", "   Extras: ${intent.extras?.keySet()?.joinToString()}")
            AmberSignerManager.handleIntentResponse(intent)
        }
    }
}

@Composable
fun AppRoot(openUrl: (Intent) -> Unit) {
    var loggedIn by remember { mutableStateOf(KeyStoreManager.hasKey()) }

    if (!loggedIn) {
        LoginScreen(
            onLoggedIn = { loggedIn = true },
            openUrl = openUrl
        )
    } else {
        AuthenticatedRoot(
            onLogout = {
                // Clear all stored keys and credentials
                KeyStoreManager.clear()
                loggedIn = false
            }
        )
    }
}

@Composable
fun AuthenticatedRoot(onLogout: () -> Unit = {}) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    
    // Shared state for meme editor image URI - avoids navigation encoding issues
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Only show bottom bar for main tabs, not for meme editor
    val showBottomBar = when (currentRoute) {
        "home", "explore", "upload", "profile" -> true
        else -> false
    }
    
    // Get current tab based on route
    val currentTab = when (currentRoute) {
        "home" -> BottomNavScreen.Home
        "explore" -> BottomNavScreen.Explore
        "upload" -> BottomNavScreen.Upload
        "profile" -> BottomNavScreen.Profile
        else -> BottomNavScreen.Home
    }

    val pubkeyHex = remember { KeyStoreManager.getPubkeyHex() }
    
    // Use state flows directly
    val connectedRelays by NostrRepository.connectedRelaysFlow.collectAsState()
    val userMetadata by NostrRepository.metadataState.collectAsState()
    
    // CRITICAL FIX: Use RelayManager's effective relays which updates when user relays are found
    val effectiveRelays by RelayManager.effectiveRelays.collectAsState()
    val totalRelays = effectiveRelays.size

    // Debug relay changes
    LaunchedEffect(effectiveRelays, connectedRelays) {
        println("🔍 MainActivity RELAY STATE:")
        println("   - Connected: $connectedRelays")
        println("   - Total: $totalRelays")
        println("   - UI Display: $connectedRelays/$totalRelays")
        if (totalRelays > 10) {
            println("   - ℹ️ Using user's ${totalRelays} preferred relays (NIP-65)")
        } else {
            println("   - ℹ️ Using ${totalRelays} fallback relays")
        }
    }

    LaunchedEffect(pubkeyHex) {
        println("🔑 MainActivity: Starting Nostr connection for pubkey: ${pubkeyHex?.take(8)}...")
        
        NostrRepository.connectAll()
        
        if (!pubkeyHex.isNullOrBlank()) {
            NostrRepository.startProfileListener(pubkeyHex)
        }
    }

    // Fetch profile data when we have connections
    LaunchedEffect(connectedRelays, pubkeyHex) {
        println("🔌 MainActivity: Connection state - connected: $connectedRelays/$totalRelays")
        
        if (!pubkeyHex.isNullOrBlank() && connectedRelays > 0) {
            println("📡 MainActivity: Fetching user profile and relays...")
            
            val (metadata, relays) = NostrRepository.fetchUserProfile(pubkeyHex)
            
            // Only update UI if we got real data
            if (metadata?.name != "Memely User" || relays.isNotEmpty()) {
                println("🎯 MainActivity: Profile fetch completed - name: '${metadata?.name ?: "null"}', relays: ${relays.size}")
            } else {
                println("⚠️ MainActivity: Profile fetch returned fallback data, waiting for real data...")
            }
        }
    }
    
    // Debug logging - improved
    LaunchedEffect(userMetadata) {
        val name = userMetadata?.name ?: "null"
        if (name != "Memely User") {
            println("👤 MainActivity: UI metadata updated - REAL name: '$name'")
        } else {
            println("👤 MainActivity: UI metadata updated - FALLBACK name: '$name'")
        }
    }

    Scaffold(
        topBar = {
            UserTopBar(
                connectedRelays = connectedRelays,
                totalRelays = totalRelays
            )
        },
        bottomBar = {
            if (showBottomBar) {
                BottomBar(
                    tabs = BottomNavScreen.values().toList(),
                    selectedTab = currentTab,
                    onTabSelected = { tab ->
                        navController.navigate(tab.name.lowercase()) {
                            // Clear back stack when navigating to main tabs
                            popUpTo("home") {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                HomeFeedScreen(
                    onTemplateSelected = { uri ->
                        // Store URI in shared state and navigate to editor
                        selectedImageUri = uri
                        navController.navigate("meme_editor")
                    }
                )
            }
            composable("explore") {
                ExploreScreen()
            }
            composable("upload") {
                UploadScreen { uri ->
                    // Store URI in shared state and navigate
                    selectedImageUri = uri
                    navController.navigate("meme_editor")
                }
            }
            composable("profile") {
                ProfileScreen(
                    user = userMetadata,
                    onLogout = onLogout
                )
            }
            composable("meme_editor") {
                if (selectedImageUri != null) {
                    MemeEditorScreen(
                        imageUri = selectedImageUri!!,
                        onDone = { savedPath ->
                            if (savedPath.isNotEmpty()) {
                                // Meme was saved successfully
                                println("✅ Meme saved to: $savedPath")
                            }
                            // Navigate back to home screen
                            selectedImageUri = null
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}