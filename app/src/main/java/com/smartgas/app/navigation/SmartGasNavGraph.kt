package com.smartgas.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartgas.app.presentation.calculator.CalculatorScreen

/** Top-level navigation destinations. */
sealed class Screen(val route: String) {
    data object Calculator : Screen("calculator")
    data object Dashboard : Screen("dashboard")
    data object TripPlanner : Screen("trip_planner")
    data object Wallet : Screen("wallet")
}

/**
 * Root Compose navigation graph for SmartGas.
 *
 * The start destination is [Screen.Dashboard]; the Advanced Calculator and
 * other screens are navigated to from there.
 */
@Composable
fun SmartGasNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Calculator.route,
    ) {
        composable(Screen.Calculator.route) {
            CalculatorScreen()
        }
        // Additional screens wired up as they are implemented.
        // composable(Screen.Dashboard.route) { DashboardScreen() }
        // composable(Screen.TripPlanner.route) { TripPlannerScreen() }
        // composable(Screen.Wallet.route) { WalletScreen() }
    }
}
