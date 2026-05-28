package dev.projectvyuh.solo.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level Solo destinations. Each appears in the bottom bar.
 *
 * Add a new entry here and it automatically shows up in the bar.
 * The route string is what Compose Navigation routes on.
 */
enum class SoloDestination(
    val route: String,
    val label: String,
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector,
) {
    CHAT(
        route        = "chat",
        label        = "Chat",
        iconOutlined = Icons.AutoMirrored.Outlined.Chat,
        iconFilled   = Icons.AutoMirrored.Rounded.Chat,
    ),
    ACTIVITY(
        route        = "activity",
        label        = "Activity",
        iconOutlined = Icons.Outlined.History,
        iconFilled   = Icons.Rounded.History,
    ),
    SETTINGS(
        route        = "settings",
        label        = "Settings",
        iconOutlined = Icons.Outlined.Settings,
        iconFilled   = Icons.Rounded.Settings,
    );

    companion object {
        val Default: SoloDestination = CHAT

        fun fromRoute(route: String?): SoloDestination? =
            entries.firstOrNull { it.route == route }
    }
}

/**
 * Non-bottom-bar routes (presented full-screen, no chrome).
 */
object SoloRoutes {
    const val ONBOARDING_GRAPH = "onboarding"
    const val MAIN_GRAPH       = "main"
}
