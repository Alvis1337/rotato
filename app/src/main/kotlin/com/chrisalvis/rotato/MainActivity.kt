package com.chrisalvis.rotato

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import com.chrisalvis.rotato.ui.BrainrotScreen
import com.chrisalvis.rotato.ui.BrainrotViewModel
import com.chrisalvis.rotato.ui.BrowseScreen
import com.chrisalvis.rotato.ui.FeedScreen
import com.chrisalvis.rotato.ui.FeedViewModel
import com.chrisalvis.rotato.ui.HomeScreen
import com.chrisalvis.rotato.ui.HomeViewModel
import com.chrisalvis.rotato.ui.SettingsScreen
import com.chrisalvis.rotato.ui.theme.RotatoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RotatoTheme {
                val homeViewModel: HomeViewModel = viewModel()
                val feedViewModel: FeedViewModel = viewModel()
                val brainrotViewModel: BrainrotViewModel = viewModel()
                val navController = rememberNavController()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val libraryRoutes = setOf("home", "feeds", "browse")
                val selectedTab = when {
                    currentRoute == "discover" -> 0
                    currentRoute in libraryRoutes -> 1
                    currentRoute == "settings" -> 2
                    else -> 0
                }

                // Hide bottom nav on full-screen detail routes
                val showBottomBar = currentRoute !in setOf("browse")

                Scaffold(
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
                        startDestination = "discover",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        composable("discover") {
                            BrainrotScreen(
                                externalViewModel = brainrotViewModel,
                                onNavigateToSettings = {
                                    navController.navigate("feeds") {
                                        popUpTo("discover") { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                viewModel = homeViewModel,
                                feedViewModel = feedViewModel,
                                onNavigateToSettings = {
                                    navController.navigate("settings") {
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToFeeds = { navController.navigate("feeds") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = homeViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToFeeds = { navController.navigate("feeds") }
                            )
                        }
                        composable("feeds") {
                            FeedScreen(
                                viewModel = feedViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onBrowseFeed = { feed ->
                                    feedViewModel.setBrowseFeed(feed)
                                    navController.navigate("browse")
                                },
                                onBrainrotFeed = { feed ->
                                    feedViewModel.setBrainrotFeed(feed)
                                    brainrotViewModel.switchFeed(feed)
                                    navController.navigate("discover") {
                                        popUpTo("discover") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable("browse") {
                            val feed = feedViewModel.browseFeed.collectAsStateWithLifecycle().value
                            feed?.let {
                                BrowseScreen(
                                    feed = it,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
