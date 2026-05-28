package dev.projectvyuh.solo.presentation.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.projectvyuh.solo.presentation.navigation.SoloDestination
import dev.projectvyuh.solo.presentation.navigation.SoloNavHost
import dev.projectvyuh.solo.presentation.navigation.navigateToRootDestination

/**
 * Top-level UI root: hosts the bottom bar and the main nav graph.
 *
 * The onboarding flow (model download, permissions) lives outside this
 * composable; SoloAppRoot is only entered once a model is installed.
 * That decision is made by MainActivity (or a wrapping composable) and
 * is not the concern of this shell.
 */
@Composable
fun SoloAppRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentDestination = SoloDestination.fromRoute(currentRoute) ?: SoloDestination.Default

    Scaffold(
        bottomBar = {
            SoloBottomBar(
                current = currentDestination,
                onSelected = { dest -> navController.navigateToRootDestination(dest) },
            )
        },
    ) { innerPadding ->
        SoloNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
