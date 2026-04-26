package com.chrisalvis.rotato

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.content.IntentCompat
import com.chrisalvis.rotato.data.RotatoPreferences
import com.chrisalvis.rotato.ui.BrainrotScreen
import com.chrisalvis.rotato.ui.BrainrotViewModel
import com.chrisalvis.rotato.ui.BrowseScreen
import com.chrisalvis.rotato.ui.LocalSourcesScreen
import com.chrisalvis.rotato.ui.HomeScreen
import com.chrisalvis.rotato.ui.HomeViewModel
import com.chrisalvis.rotato.ui.MalViewModel
import com.chrisalvis.rotato.ui.SettingsScreen
import com.chrisalvis.rotato.ui.ScheduleScreen
import com.chrisalvis.rotato.ui.SetupScreen
import com.chrisalvis.rotato.ui.theme.RotatoTheme
import com.chrisalvis.rotato.worker.ScheduleReceiver

class MainActivity : AppCompatActivity() {

    private lateinit var malViewModelRef: MalViewModel
    private val _pendingNavigate = mutableStateOf<String?>(null)
    private val _pendingSharedImages = mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _pendingNavigate.value = intent.getStringExtra(ScheduleReceiver.EXTRA_NAVIGATE_TO)
        _pendingSharedImages.value = extractSharedImages(intent)
        setContent {
            RotatoTheme {
                val rotatoPrefs = remember { RotatoPreferences(applicationContext) }
                val setupDone by rotatoPrefs.setupDone.collectAsStateWithLifecycle(initialValue = null)

                if (setupDone == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                    return@RotatoTheme
                }

                val homeViewModel: HomeViewModel = viewModel()
                val brainrotViewModel: BrainrotViewModel = viewModel()
                val malViewModel: MalViewModel = viewModel()
                malViewModelRef = malViewModel
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
                    "settings" -> 3
                    else -> 0
                }

                val showBottomBar = currentRoute !in setOf("sources", "setup")
                    && brainrotViewModel.selectedItem.collectAsStateWithLifecycle().value == null

                // Handle navigation from notification (e.g., locked collection alert)
                val pendingNav = _pendingNavigate.value
                LaunchedEffect(pendingNav) {
                    if (pendingNav == "browse") {
                        navController.navigate("browse") {
                            popUpTo("discover") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        _pendingNavigate.value = null
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = {
                                        navController.navigate("discover") {
                                            popUpTo("discover") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Discover") },
                                    label = { Text("Discover") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = {
                                        homeViewModel.refreshFromFeeds()
                                        navController.navigate("home") {
                                            popUpTo("discover") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Wallpaper, contentDescription = "Library") },
                                    label = { Text("Library") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = {
                                        navController.navigate("browse") {
                                            popUpTo("discover") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = "Collections") },
                                    label = { Text("Collections") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = {
                                        navController.navigate("settings") {
                                            popUpTo("discover") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") }
                                )
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
                        composable("discover") {
                            BrainrotScreen(
                                externalViewModel = brainrotViewModel,
                                onNavigateToSettings = {
                                    navController.navigate("home") {
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
                                viewModel = homeViewModel,
                                malViewModel = malViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToSources = { navController.navigate("sources") },
                                onNavigateToSchedule = { navController.navigate("schedule") },
                            )
                        }
                        composable("sources") {
                            LocalSourcesScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("schedule") {
                            ScheduleScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable("browse") {
                            BrowseScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = intent.data
        if (uri?.scheme == "rotato" && uri.host == "callback") {
            val code = uri.getQueryParameter("code") ?: return
            if (::malViewModelRef.isInitialized) {
                malViewModelRef.handleCallback(code)
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

