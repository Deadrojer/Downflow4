package com.downflow.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.downflow.app.ui.screens.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Downloads : Screen("downloads", "Downloads", Icons.Filled.Download)
    object Browser   : Screen("browser",   "Browser",   Icons.Filled.Language)
    object History   : Screen("history",   "History",   Icons.Filled.History)
    object Settings  : Screen("settings",  "Settings",  Icons.Filled.Settings)
    object Detail    : Screen("detail/{gid}", "Detail", Icons.Filled.Info) {
        fun createRoute(gid: String) = "detail/$gid"
    }
}

val bottomNavScreens = listOf(
    Screen.Downloads,
    Screen.Browser,
    Screen.History,
    Screen.Settings,
)

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Downloads.route) {
        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onNavigateToDetail = { gid ->
                    navController.navigate(Screen.Detail.createRoute(gid))
                }
            )
        }
        composable(Screen.Browser.route) {
            BrowserScreen()
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.Detail.route) { backStackEntry ->
            val gid = backStackEntry.arguments?.getString("gid") ?: return@composable
            DownloadDetailScreen(
                gid = gid,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
