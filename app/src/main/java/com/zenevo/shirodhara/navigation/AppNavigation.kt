package com.zenevo.shirodhara.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zenevo.shirodhara.ui.screens.DashboardScreen
import com.zenevo.shirodhara.ui.screens.SplashScreen
import com.zenevo.shirodhara.ui.screens.TreatmentScreen

object Destinations {
    const val SPLASH = "splash" // New destination for the splash screen
    const val DASHBOARD = "dashboard"
    const val TREATMENT = "treatment"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    onFindDevice: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH // Set the splash screen as the starting point
    ) {
        // Composable for the Splash Screen
        composable(Destinations.SPLASH) {
            SplashScreen(
                onTimeout = {
                    // Navigate to the dashboard after the timeout
                    navController.navigate(Destinations.DASHBOARD) {
                        // Remove the splash screen from the back stack
                        popUpTo(Destinations.SPLASH) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        // Composable for the Dashboard Screen
        composable(Destinations.DASHBOARD) {
            DashboardScreen(
                onStartTreatment = { duration, temperature ->
                    navController.navigate("${Destinations.TREATMENT}/$duration/$temperature")
                },
                onFindDevice = onFindDevice
            )
        }

        composable(
            route = "${Destinations.TREATMENT}/{duration}/{temperature}",
            arguments = listOf(
                navArgument("duration") { type = NavType.IntType },
                navArgument("temperature") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val duration = backStackEntry.arguments?.getInt("duration") ?: 30
            val temperature = backStackEntry.arguments?.getInt("temperature") ?: 37
            TreatmentScreen(
                navController = navController,
                duration = duration,
                temperature = temperature
            )
        }
    }
}
