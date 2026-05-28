package dev.projectvyuh.solo.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.projectvyuh.solo.presentation.activity.ActivityScreen
import dev.projectvyuh.solo.presentation.chat.ChatScreen
import dev.projectvyuh.solo.presentation.settings.SettingsScreen

/**
 * Main app graph — the three top-level destinations behind the bottom bar.
 *
 * Onboarding is a separate top-level graph rooted at SoloRoutes.ONBOARDING_GRAPH;
 * the root composable (SoloApp) decides which graph to enter based on whether
 * the primary model is installed.
 */
@Composable
fun SoloNavHost(
    navController: NavHostController,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = SoloDestination.Default.route,
        modifier = modifier,
    ) {
        composable(SoloDestination.CHAT.route)     { ChatScreen() }
        composable(SoloDestination.ACTIVITY.route) { ActivityScreen() }
        composable(SoloDestination.SETTINGS.route) { SettingsScreen() }
    }
}

/**
 * Top-bar-friendly navigate: clears back stack when switching between root
 * destinations so the back gesture exits the app instead of cycling tabs.
 */
fun NavHostController.navigateToRootDestination(destination: SoloDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState    = true
    }
}
