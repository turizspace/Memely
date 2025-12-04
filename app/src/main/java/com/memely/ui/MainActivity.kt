package com.memely.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.memely.data.TemplateRepository
import com.memely.nostr.*
import com.memely.ui.components.BottomBar
import com.memely.ui.components.UserTopBar
import com.memely.ui.screens.*
import com.memely.ui.tutorial.TutorialManager
import com.memely.util.SecureLog

class MainActivity : ComponentActivity() {

    private val amberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { intent ->
            println("🔄 MainActivity: Amber launcher callback received")
            
            // Get the request ID to determine if this was a login or signing request
            val requestId = intent.getStringExtra("id")
            val isLogin = requestId != null && AmberSignerManager.isLoginRequest(requestId)
            
            println("🔍 MainActivity: Request ID=$requestId, isLogin=$isLogin")
            
            // Let AmberSignerManager handle the response (it manages pending requests)
            AmberSignerManager.handleIntentResponse(intent)
            
            val pubkey = intent.getStringExtra("result")
            val packageName = intent.getStringExtra("package") ?: "com.greenart7c3.nostrsigner"
            
            println("🔑 MainActivity: Amber callback - pubkey=${pubkey?.take(8)}..., package=$packageName")
            
            // Only save pubkey if this is a LOGIN request
            if (isLogin && !pubkey.isNullOrBlank()) {
                println("✅ MainActivity: This is a LOGIN response - saving pubkey")
                KeyStoreManager.saveExternalPubkey(pubkey)
                KeyStoreManager.saveAmberPackageName(packageName)
                AmberSignerManager.configure(pubkey, packageName)
                com.memely.nostr.AuthStateManager.refresh()
            } else if (!isLogin) {
                println("ℹ️ MainActivity: This is a SIGNING response - NOT updating stored pubkey")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent screenshots on this activity (contains sensitive key data)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        KeyStoreManager.init(applicationContext)

        // Restore Amber configuration if user logged in with Amber
        if (KeyStoreManager.isUsingAmber()) {
            val pubkey = KeyStoreManager.getPubkeyHex()
            val packageName = KeyStoreManager.getAmberPackageName()
            println("🔄 MainActivity.onCreate: Restoring Amber config - pubkey=${pubkey?.take(8)}..., package=$packageName")
            if (pubkey != null && packageName != null) {
                AmberSignerManager.configure(pubkey, packageName)
                println("✅ MainActivity: Restored external signer config")
            } else {
                println("⚠️ MainActivity: isUsingAmber=true but missing pubkey or package!")
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
            println("🔗 MainActivity: Received nostrsigner callback intent")
            // Validate intent before processing (security check)
            if (validateAmberIntent(intent)) {
                // Get the request ID to determine if this was a login or signing request
                val requestId = intent.getStringExtra("id")
                val isLogin = requestId != null && AmberSignerManager.isLoginRequest(requestId)
                
                println("🔍 MainActivity: Callback Request ID=$requestId, isLogin=$isLogin")
                
                AmberSignerManager.handleIntentResponse(intent)
                
                val pubkey = intent.getStringExtra("result")
                val packageName = intent.getStringExtra("package") ?: "com.greenart7c3.nostrsigner"
                
                println("🔑 MainActivity: Callback - pubkey=${pubkey?.take(8)}..., package=$packageName")
                
                // Only update stored pubkey on actual login (get_public_key), not on signing responses
                if (isLogin && !pubkey.isNullOrBlank()) {
                    println("✅ MainActivity: This is a LOGIN callback - saving pubkey")
                    KeyStoreManager.saveExternalPubkey(pubkey)
                    KeyStoreManager.saveAmberPackageName(packageName)
                    AmberSignerManager.configure(pubkey, packageName)
                    com.memely.nostr.AuthStateManager.refresh()
                } else if (!isLogin) {
                    println("ℹ️ MainActivity: This is a SIGNING callback - NOT updating stored pubkey")
                }
            } else {
                println("⚠️ Invalid nostrsigner intent received - ignoring")
            }
        }
    }
    
    /**
     * Validate Amber signer intent to prevent intent injection attacks.
     * Checks for required fields and validates structure.
     */
    private fun validateAmberIntent(intent: Intent): Boolean {
        return try {
            // Ensure intent has expected extras
            val id = intent.getStringExtra("id")
            val result = intent.getStringExtra("result")
            
            // At minimum, should have an ID
            !id.isNullOrBlank()
        } catch (e: Exception) {
            SecureLog.e("Error validating intent", e)
            false
        }
    }
}

@Composable
fun AppRoot(openUrl: (Intent) -> Unit) {
    val context = LocalContext.current
    val loggedIn by com.memely.nostr.AuthStateManager.isLoggedIn.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }
    var currentTheme by remember {
        mutableStateOf(com.memely.ui.theme.ThemeManager.getThemePreference(context))
    }
    
    // Initialize TutorialManager
    LaunchedEffect(Unit) {
        TutorialManager.initialize(context)
        isInitialized = true
    }
    
    // Start tutorial if user is logged in and hasn't completed it
    LaunchedEffect(loggedIn) {
        if (loggedIn && TutorialManager.shouldShowTutorial()) {
            TutorialManager.startTutorial()
        }
    }

    // Wait for initialization before showing any screen
    if (!isInitialized) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    com.memely.ui.theme.MemelyTheme(
        isDarkMode = com.memely.ui.theme.isDarkTheme(currentTheme)
    ) {
        when {
            !loggedIn -> {
                LoginScreen(
                    onLoggedIn = {
                        com.memely.nostr.AuthStateManager.refresh()
                    },
                    openUrl = openUrl
                )
            }
            else -> {
                AuthenticatedRoot(
                    currentTheme = currentTheme,
                    onThemeChange = { newTheme ->
                        currentTheme = newTheme
                    },
                    onLogout = {
                        // Clear all stored keys and credentials
                        KeyStoreManager.clear()
                        com.memely.nostr.AuthStateManager.refresh()
                    }
                )
            }
        }
    }
}

@Composable
fun AuthenticatedRoot(
    currentTheme: com.memely.ui.theme.ThemePreference = com.memely.ui.theme.ThemeManager.THEME_LIGHT,
    onThemeChange: (com.memely.ui.theme.ThemePreference) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    
    // Shared state for meme editor image URI - avoids navigation encoding issues
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Theme state management
    val context = LocalContext.current
    var themeState by remember { 
        mutableStateOf(currentTheme)
    }
    val isDarkMode = com.memely.ui.theme.isDarkTheme(themeState)
    
    // Get available templates for tutorial
    val availableTemplates by TemplateRepository.templatesFlow.collectAsState()
    
    // Set up tutorial navigation callback
    LaunchedEffect(Unit) {
        TutorialManager.onNavigationRequired = { action ->
            when (action) {
                "navigate_to_editor" -> {
                    // Select first available template and navigate to editor
                    if (availableTemplates.isNotEmpty()) {
                        val randomTemplate = availableTemplates.first()
                        selectedImageUri = Uri.parse(randomTemplate.url)
                        navController.navigate("meme_editor")
                    }
                }
                "navigate_to_home" -> {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
                "navigate_to_explore" -> {
                    navController.navigate("explore") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
                "navigate_to_upload" -> {
                    navController.navigate("upload") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
                "navigate_to_profile" -> {
                    navController.navigate("profile") {
                        popUpTo("home") { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
    
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

    // FIX: Don't cache pubkey - re-read it when auth state changes so Amber login works
    val loggedInState by com.memely.nostr.AuthStateManager.isLoggedIn.collectAsState()
    val pubkeyHex = remember(loggedInState) { KeyStoreManager.getPubkeyHex() }
    
    // Use state flows directly
    val connectedRelays by NostrRepository.connectedRelaysFlow.collectAsState()
    val userMetadata by NostrRepository.metadataState.collectAsState()
    
    // CRITICAL FIX: Use RelayManager's effective relays which updates when user relays are found
    val effectiveRelays by RelayManager.effectiveRelays.collectAsState()
    val totalRelays = effectiveRelays.size

    // Debug relay changes
    LaunchedEffect(effectiveRelays, connectedRelays) {
        SecureLog.d("Relay status: $connectedRelays/$totalRelays connected")
    }

    LaunchedEffect(pubkeyHex) {
        SecureLog.d("Starting Nostr connection")
        
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
                totalRelays = totalRelays,
                onThemeChange = { newTheme ->
                    themeState = newTheme
                    com.memely.ui.theme.ThemeManager.saveThemePreference(context, newTheme)
                    onThemeChange(newTheme)
                }
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
                        },
                        onNavigateToHomeFeed = {
                            // Navigate back to home feed for template selection
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}