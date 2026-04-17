package com.chrisalvis.rotato

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                // Single ViewModel instance shared between both screens
                val homeViewModel: HomeViewModel = viewModel()
                val feedViewModel: FeedViewModel = viewModel()
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            viewModel = homeViewModel,
                            feedViewModel = feedViewModel,
                            onNavigateToSettings = { navController.navigate("settings") },
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
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
