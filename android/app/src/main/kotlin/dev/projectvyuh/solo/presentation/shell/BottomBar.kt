package dev.projectvyuh.solo.presentation.shell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.presentation.navigation.SoloDestination
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloBackground
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground

/**
 * Bottom navigation bar for the three top-level destinations.
 *
 * Visual treatment: flat (no elevation), background matches app surface,
 * active item is violet, inactive is muted. Matches the "subtraction over
 * addition" design philosophy — no extra dividers or shadows.
 */
@Composable
fun SoloBottomBar(
    current: SoloDestination,
    onSelected: (SoloDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = SoloBackground,
        tonalElevation = 0.dp,
    ) {
        SoloDestination.entries.forEach { dest ->
            val selected = dest == current
            NavigationBarItem(
                selected = selected,
                onClick  = { if (!selected) onSelected(dest) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.iconFilled else dest.iconOutlined,
                        contentDescription = dest.label,
                    )
                },
                label = { Text(dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = SoloAccent,
                    selectedTextColor   = SoloAccent,
                    indicatorColor      = Color.Transparent,
                    unselectedIconColor = SoloMutedForeground,
                    unselectedTextColor = SoloMutedForeground,
                ),
            )
        }
    }
}
