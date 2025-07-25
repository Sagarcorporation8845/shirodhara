package com.zenevo.shirodhara.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zenevo.shirodhara.ui.screens.DashboardScreen
import com.zenevo.shirodhara.ui.screens.TreatmentScreen

object Destinations {
    const val DASHBOARD = "dashboard"
    const val TREATMENT = "treatment"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Destinations.DASHBOARD,
    onFindDevice: () -> Unit // Parameter for the find device action
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Destinations.DASHBOARD) {
            DashboardScreen(
                onStartTreatment = { duration, temperature ->
                    navController.navigate("${Destinations.TREATMENT}/$duration/$temperature")
                },
                onFindDevice = onFindDevice // Pass the action to the dashboard
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
