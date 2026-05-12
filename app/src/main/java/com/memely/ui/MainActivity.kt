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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.memely.di.appContainer
import com.memely.di.viewModelFactory
import com.memely.data.TemplateRepository
import com.memely.nostr.*
import com.memely.ui.components.BottomBar
import com.memely.ui.components.UserTopBar
import com.memely.ui.screens.*
import com.memely.ui.tutorial.TutorialManager
import com.memely.ui.viewmodels.AppRootViewModel
import com.memely.ui.viewmodels.AuthenticatedRootViewModel
import com.memely.util.SecureLog

class MainActivity : ComponentActivity() {

    private val amberLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { intent ->
            SecureLog.d("MainActivity: Amber launcher callback received")
            
            // Get the request ID to determine if this was a login or signing request
            val requestId = intent.getStringExtra("id")
            val isLogin = requestId != null && AmberSignerManager.isLoginRequest(requestId)
            
            SecureLog.d("MainActivity: Request ${requestId ?: "unknown"} isLogin=$isLogin")
            
            // Let AmberSignerManager handle the response (it manages pending requests)
            AmberSignerManager.handleIntentResponse(intent)
            
            val pubkey = intent.getStringExtra("result")
            val packageName = intent.getStringExtra("package") ?: "com.greenart7c3.nostrsigner"
            
            SecureLog.d("MainActivity: Amber callback package=$packageName pubkey=${pubkey?.let { SecureLog.truncateHex(it) }}")
            
            // Only save pubkey if this is a LOGIN request
            if (isLogin && !pubkey.isNullOrBlank()) {
                SecureLog.d("MainActivity: Persisting Amber login response")
                KeyStoreManager.saveExternalPubkey(pubkey)
                KeyStoreManager.saveAmberPackageName(packageName)
                AmberSignerManager.configure(pubkey, packageName)
                com.memely.nostr.AuthStateManager.refresh()
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

        // Restore Amber configuration if user logged in with Amber
        if (KeyStoreManager.isUsingAmber()) {
            val pubkey = KeyStoreManager.getPubkeyHex()
            val packageName = KeyStoreManager.getAmberPackageName()
            if (pubkey != null && packageName != null) {
                AmberSignerManager.configure(pubkey, packageName)
                SecureLog.d("MainActivity: Restored Amber signer configuration")
            } else {
                SecureLog.w("MainActivity: Amber flagged as active but persisted state is incomplete")
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
            SecureLog.d("MainActivity: Received nostrsigner callback")
            // Validate intent before processing (security check)
            if (validateAmberIntent(intent)) {
                // Get the request ID to determine if this was a login or signing request
                val requestId = intent.getStringExtra("id")
                val isLogin = requestId != null && AmberSignerManager.isLoginRequest(requestId)
                
                SecureLog.d("MainActivity: Callback request ${requestId ?: "unknown"} isLogin=$isLogin")
                
                AmberSignerManager.handleIntentResponse(intent)
                
                val pubkey = intent.getStringExtra("result")
                val packageName = intent.getStringExtra("package") ?: "com.greenart7c3.nostrsigner"
                
                SecureLog.d("MainActivity: Callback package=$packageName pubkey=${pubkey?.let { SecureLog.truncateHex(it) }}")
                
                // Only update stored pubkey on actual login (get_public_key), not on signing responses
                if (isLogin && !pubkey.isNullOrBlank()) {
                    SecureLog.d("MainActivity: Persisting Amber login callback")
                    KeyStoreManager.saveExternalPubkey(pubkey)
                    KeyStoreManager.saveAmberPackageName(packageName)
                    AmberSignerManager.configure(pubkey, packageName)
                    com.memely.nostr.AuthStateManager.refresh()
                }
            } else {
                SecureLog.w("MainActivity: Ignoring invalid nostrsigner callback")
            }
        }
    }
    
    /**
     * Validate Amber signer intent to prevent intent injection attacks.
     * Checks for required fields and validates structure.
     */
    private fun validateAmberIntent(intent: Intent): Boolean {
        return try {
            if (intent.action != null && intent.action != Intent.ACTION_VIEW) {
                return false
            }

            if (intent.scheme != "nostrsigner") {
                return false
            }

            val id = intent.getStringExtra("id")
            val result = intent.getStringExtra("result")
            val event = intent.getStringExtra("event")

            if (id.isNullOrBlank() || id.length > 128 || !id.matches(Regex("^[A-Za-z0-9_-]+$"))) {
                return false
            }

            if (!intent.hasExtra("result") && !intent.hasExtra("event") && !intent.hasExtra("package")) {
                return false
            }

            if (result != null && result.length > 10000) {
                return false
            }

            if (event != null) {
                if (event.length > 100000) {
                    return false
                }
                runCatching { org.json.JSONObject(event) }.getOrElse {
                    return false
                }
            }

            true
        } catch (e: Exception) {
            SecureLog.e("Error validating intent", e)
            false
        }
    }
}

@Composable
fun AppRoot(openUrl: (Intent) -> Unit) {
    val context = LocalContext.current
    val appContainer = remember(context) { context.appContainer }
    val appRootViewModel: AppRootViewModel = viewModel(
        factory = remember(appContainer) {
            viewModelFactory {
                AppRootViewModel(
                    sessionRepository = appContainer.sessionRepository,
                    themeRepository = appContainer.themeRepository
                )
            }
        }
    )
    val uiState by appRootViewModel.uiState.collectAsState()

    // Start tutorial if user is logged in and hasn't completed it
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn && TutorialManager.shouldShowTutorial()) {
            TutorialManager.startTutorial()
        }
    }

    com.memely.ui.theme.MemelyTheme(
        isDarkMode = com.memely.ui.theme.isDarkTheme(uiState.currentTheme)
    ) {
        when {
            !uiState.isLoggedIn -> {
                LoginScreen(
                    onLoggedIn = appRootViewModel::refreshAuth,
                    openUrl = openUrl
                )
            }
            else -> {
                AuthenticatedRoot(
                    currentTheme = uiState.currentTheme,
                    onThemeChange = appRootViewModel::updateTheme,
                    onLogout = appRootViewModel::logout
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
    val context = LocalContext.current
    val appContainer = remember(context) { context.appContainer }
    val authenticatedRootViewModel: AuthenticatedRootViewModel = viewModel(
        factory = remember(appContainer) {
            viewModelFactory {
                AuthenticatedRootViewModel(
                    sessionRepository = appContainer.sessionRepository,
                    profileRepository = appContainer.profileRepository
                )
            }
        }
    )
    val rootUiState by authenticatedRootViewModel.uiState.collectAsState()
    
    // Shared state for meme editor image URI - avoids navigation encoding issues
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // Theme state management
    var themeState by remember { 
        mutableStateOf(currentTheme)
    }
    
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

    val pubkeyHex = rootUiState.pubkeyHex
    val connectedRelays = rootUiState.connectedRelays
    val totalRelays = rootUiState.totalRelays
    val userMetadata = rootUiState.userMetadata

    // Debug relay changes
    LaunchedEffect(connectedRelays, totalRelays) {
        SecureLog.d("Relay status: $connectedRelays/$totalRelays connected")
    }

    LaunchedEffect(pubkeyHex) {
        authenticatedRootViewModel.startSession(pubkeyHex)
    }

    // Fetch profile data when we have connections
    LaunchedEffect(connectedRelays, pubkeyHex) {
        authenticatedRootViewModel.refreshProfileIfNeeded(pubkeyHex, connectedRelays)
    }
    
    // Debug logging - improved
    LaunchedEffect(userMetadata) {
        val name = userMetadata?.name ?: "null"
        if (name != "Memely User") {
            SecureLog.d("MainActivity: UI metadata updated name='$name'")
        } else {
            SecureLog.d("MainActivity: UI metadata still using fallback metadata")
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
                                SecureLog.d("MainActivity: Meme saved successfully")
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
