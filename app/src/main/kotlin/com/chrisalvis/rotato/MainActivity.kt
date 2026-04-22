package com.chrisalvis.rotato

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _pendingNavigate.value = intent.getStringExtra(ScheduleReceiver.EXTRA_NAVIGATE_TO)
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
        // Handle navigation extras from notifications
        intent.getStringExtra(ScheduleReceiver.EXTRA_NAVIGATE_TO)?.let {
            _pendingNavigate.value = it
        }
    }
}

