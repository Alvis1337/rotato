package com.chrisalvis.rotato

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.data.plugins.PluginRepository
import com.chrisalvis.rotato.ui.BrainrotScreen
import com.chrisalvis.rotato.ui.BrainrotViewModel
import com.chrisalvis.rotato.ui.AboutDataSettingsScreen
import com.chrisalvis.rotato.ui.BrowseScreen
import com.chrisalvis.rotato.ui.DiscoverSourcesSettingsScreen
import com.chrisalvis.rotato.ui.IntegrationsSettingsScreen
import com.chrisalvis.rotato.ui.LocalSourcesScreen
import com.chrisalvis.rotato.ui.NsfwPrivacySettingsScreen
import com.chrisalvis.rotato.ui.PluginStoreScreen
import com.chrisalvis.rotato.ui.RotationWallpaperSettingsScreen
import com.chrisalvis.rotato.ui.HomeScreen
import com.chrisalvis.rotato.ui.HomeViewModel
import com.chrisalvis.rotato.ui.MalViewModel
import com.chrisalvis.rotato.ui.SettingsScreen
import com.chrisalvis.rotato.ui.ScheduleScreen
import com.chrisalvis.rotato.ui.SetupScreen
import com.chrisalvis.rotato.ui.SourceHealthScreen
import com.chrisalvis.rotato.ui.StatsScreen
import com.chrisalvis.rotato.ui.TasteScreen
import com.chrisalvis.rotato.ui.TasteViewModel
import com.chrisalvis.rotato.ui.theme.RotatoTheme
import com.chrisalvis.rotato.ui.theme.ThemeMode
import com.chrisalvis.rotato.worker.ScheduleReceiver

class MainActivity : AppCompatActivity() {

    private lateinit var malViewModelRef: MalViewModel
    private val _pendingNavigate = mutableStateOf<String?>(null)
    private val _pendingSharedImages = mutableStateOf<List<Uri>>(emptyList())
    private val _pendingMalCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only consume the launch intent once. Folding/unfolding (or rotating) recreates the
        // activity with the same intent, which would otherwise re-show the share dialog the
        // user already answered and re-run notification navigation.
        if (savedInstanceState == null) {
            _pendingNavigate.value = intent.getStringExtra(ScheduleReceiver.EXTRA_NAVIGATE_TO)
            _pendingSharedImages.value = extractSharedImages(intent)
            // The MAL OAuth redirect can cold-start the activity (e.g. the process was killed
            // while the browser was open); onNewIntent never sees it in that case.
            _pendingMalCode.value = malCallbackCode(intent)
        } else {
            // Keep a share/navigation the user hasn't acted on yet across the recreation.
            _pendingNavigate.value = savedInstanceState.getString(STATE_PENDING_NAV)
            _pendingSharedImages.value = BundleCompat.getParcelableArrayList(
                savedInstanceState, STATE_PENDING_SHARED, Uri::class.java
            ) ?: emptyList()
            _pendingMalCode.value = savedInstanceState.getString(STATE_PENDING_MAL_CODE)
        }
        setContent {
            val themePrefs = remember { RotatoPreferences(applicationContext) }
            val themeMode by themePrefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by themePrefs.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            RotatoTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                val rotatoPrefs = remember { RotatoPreferences(applicationContext) }
                val setupDone by rotatoPrefs.setupDone.collectAsStateWithLifecycle(initialValue = null)

                // Migration: if user upgraded from pre-store-first build, auto-install bundled plugins
                val pluginRepo = remember { PluginRepository(applicationContext) }
                LaunchedEffect(setupDone) {
                    if (setupDone == true) pluginRepo.autoMigrateIfNeeded()
                }

                if (setupDone == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    }
                    return@RotatoTheme
                }

                val homeViewModel: HomeViewModel = viewModel()
                val brainrotViewModel: BrainrotViewModel = viewModel()
                val malViewModel: MalViewModel = viewModel()
                val tasteViewModel: TasteViewModel = viewModel()
                malViewModelRef = malViewModel
                val pendingMalCode = _pendingMalCode.value
                LaunchedEffect(pendingMalCode) {
                    val code = pendingMalCode ?: return@LaunchedEffect
                    malViewModel.handleCallback(code)
                    _pendingMalCode.value = null
                }
                val navController = rememberNavController()

                val pendingShared = _pendingSharedImages.value
                if (pendingShared.isNotEmpty() && setupDone == true) {
                    SharedImageDialog(
                        count = pendingShared.size,
                        onAddToLibrary = {
                            homeViewModel.addImages(pendingShared)
                            _pendingSharedImages.value = emptyList()
                        },
                        onDismiss = { _pendingSharedImages.value = emptyList() }
                    )
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val selectedTab = when (currentRoute) {
                    "discover" -> 0
                    "home" -> 1
                    "browse" -> 2
                    "taste" -> 3
                    "settings", "sources", "schedule", "stats", "source_health",
                    "settings_rotation_wallpaper", "settings_nsfw_privacy", "settings_discover_sources",
                    "settings_integrations", "settings_about_data" -> 4
                    else -> 0
                }

                val showBottomBar = currentRoute !in setOf("sources", "source_health", "setup", "onboarding", "plugin_store")
                    && brainrotViewModel.selectedItem.collectAsStateWithLifecycle().value == null

                // Handle navigation from notifications, shortcuts, or schedule receiver
                val pendingNav = _pendingNavigate.value
                LaunchedEffect(pendingNav) {
                    val route = pendingNav ?: return@LaunchedEffect
                    navController.navigate(route) {
                        popUpTo("discover") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    _pendingNavigate.value = null
                }

                // Unfolded foldables / tablets get a side rail instead of a stretched bottom bar.
                val useNavRail = LocalConfiguration.current.screenWidthDp >= 600
                val navTabs = listOf(
                    NavTab("discover", "Discover", Icons.Default.AutoAwesome),
                    NavTab("home", "Library", Icons.Default.Wallpaper),
                    NavTab("browse", "Collections", Icons.Default.BookmarkBorder),
                    NavTab("taste", "Taste", Icons.Default.Tune),
                    NavTab("settings", "Settings", Icons.Default.Settings),
                )
                val onTabSelected: (Int) -> Unit = { index ->
                    val route = navTabs[index].route
                    if (route == "home") homeViewModel.refreshFromFeeds()
                    navController.navigate(route) {
                        if (route == "discover") {
                            popUpTo("discover") { inclusive = false }
                        } else {
                            popUpTo("discover") { saveState = true }
                            restoreState = true
                        }
                        launchSingleTop = true
                    }
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    if (showBottomBar && useNavRail) {
                        NavigationRail {
                            Spacer(Modifier.weight(1f))
                            navTabs.forEachIndexed { index, tab ->
                                NavigationRailItem(
                                    selected = selectedTab == index,
                                    onClick = { onTabSelected(index) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Scaffold(
                        modifier = Modifier.weight(1f),
                        contentWindowInsets = WindowInsets(0),
                        bottomBar = {
                            if (showBottomBar && !useNavRail) {
                                NavigationBar {
                                    navTabs.forEachIndexed { index, tab ->
                                        NavigationBarItem(
                                            selected = selectedTab == index,
                                            onClick = { onTabSelected(index) },
                                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                                            label = { Text(tab.label) }
                                        )
                                    }
                                }
                            }
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = if (setupDone == true) "discover" else "setup",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            composable("setup") {
                                SetupScreen(
                                    onSetupComplete = {
                                        navController.navigate("discover") {
                                            popUpTo("setup") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("onboarding") {
                                SetupScreen(
                                    onSetupComplete = { navController.popBackStack() }
                                )
                            }
                            composable("discover") {
                                BrainrotScreen(
                                    externalViewModel = brainrotViewModel,
                                    onNavigateToSettings = {
                                        navController.navigate("settings") {
                                            popUpTo("discover") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onNavigateToSources = { navController.navigate("sources") }
                                )
                            }
                            composable("home") {
                                HomeScreen(
                                    viewModel = homeViewModel,
                                    onBrowseFeed = { navController.navigate("browse") }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToRotationWallpaper = { navController.navigate("settings_rotation_wallpaper") },
                                    onNavigateToNsfwPrivacy = { navController.navigate("settings_nsfw_privacy") },
                                    onNavigateToDiscoverSources = { navController.navigate("settings_discover_sources") },
                                    onNavigateToIntegrations = { navController.navigate("settings_integrations") },
                                    onNavigateToAboutData = { navController.navigate("settings_about_data") },
                                )
                            }
                            composable("settings_rotation_wallpaper") {
                                RotationWallpaperSettingsScreen(
                                    viewModel = homeViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToSchedule = { navController.navigate("schedule") },
                                )
                            }
                            composable("settings_nsfw_privacy") {
                                NsfwPrivacySettingsScreen(
                                    viewModel = homeViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }
                            composable("settings_discover_sources") {
                                DiscoverSourcesSettingsScreen(
                                    viewModel = homeViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToSources = { navController.navigate("sources") },
                                )
                            }
                            composable("settings_integrations") {
                                IntegrationsSettingsScreen(
                                    malViewModel = malViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                )
                            }
                            composable("settings_about_data") {
                                AboutDataSettingsScreen(
                                    viewModel = homeViewModel,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToStats = { navController.navigate("stats") },
                                    onNavigateToSourceHealth = { navController.navigate("source_health") },
                                    onShowOnboarding = { navController.navigate("onboarding") },
                                )
                            }
                            composable("sources") {
                                LocalSourcesScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToPluginStore = { navController.navigate("plugin_store") }
                                )
                            }
                            composable("plugin_store") {
                                PluginStoreScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("schedule") {
                                ScheduleScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("stats") {
                                StatsScreen(onNavigateBack = { navController.popBackStack() })
                            }
                            composable("source_health") {
                                SourceHealthScreen(
                                    brainrotViewModel = brainrotViewModel,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                            composable("browse") {
                                BrowseScreen(onGoToDiscover = { navController.navigate("discover") })
                            }
                            composable("taste") {
                                TasteScreen(vm = tasteViewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDING_NAV, _pendingNavigate.value)
        outState.putParcelableArrayList(STATE_PENDING_SHARED, ArrayList(_pendingSharedImages.value))
        outState.putString(STATE_PENDING_MAL_CODE, _pendingMalCode.value)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data
        if (uri?.scheme == "rotato" && uri.host == "callback") {
            val code = malCallbackCode(intent) ?: return
            if (::malViewModelRef.isInitialized) {
                malViewModelRef.handleCallback(code)
            } else {
                // Still on the splash/setup gate; handled once the ViewModel exists.
                _pendingMalCode.value = code
            }
            return
        }
        intent.getStringExtra(ScheduleReceiver.EXTRA_NAVIGATE_TO)?.let {
            _pendingNavigate.value = it
        }
        val shared = extractSharedImages(intent)
        if (shared.isNotEmpty()) {
            _pendingSharedImages.value = shared
        }
    }

    private fun malCallbackCode(intent: Intent): String? {
        val uri = intent.data ?: return null
        if (uri.scheme != "rotato" || uri.host != "callback") return null
        return uri.getQueryParameter("code")
    }

    private fun extractSharedImages(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            }
            else -> emptyList()
        }
    }
}

private const val STATE_PENDING_NAV = "pending_nav"
private const val STATE_PENDING_SHARED = "pending_shared"
private const val STATE_PENDING_MAL_CODE = "pending_mal_code"

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun SharedImageDialog(
    count: Int,
    onAddToLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Rotato") },
        text = {
            Text(
                if (count == 1) "Add this image to your rotation library?"
                else "Add $count images to your rotation library?"
            )
        },
        confirmButton = {
            TextButton(onClick = onAddToLibrary) { Text("Add to Library") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

