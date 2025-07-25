package com.zenevo.shirodhara.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zenevo.shirodhara.ui.screens.DashboardScreen
import com.zenevo.shirodhara.ui.screens.TreatmentScreen

object Destinations {
    const val DASHBOARD = "dashboard"
    const val TREATMENT = "treatment"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Destinations.DASHBOARD
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Destinations.DASHBOARD) {
            DashboardScreen(
                onStartTreatment = { duration, temperature ->
                    navController.navigate("${Destinations.TREATMENT}/$duration/$temperature")
                }
            )
        }
        
        composable(
            "${Destinations.TREATMENT}/{duration}/{temperature}"
        ) {
            TreatmentScreen(
                navController = navController
            )
        }
    }
} 