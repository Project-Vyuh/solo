package dev.projectvyuh.solo.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SoloDarkColors = darkColorScheme(
    background           = SoloBackground,
    onBackground         = SoloForeground,
    surface              = SoloSurface,
    onSurface            = SoloForeground,
    surfaceContainer     = SoloSurface,
    surfaceContainerHigh = SoloSurfaceElevated,
    surfaceVariant       = SoloSurfaceElevated,
    onSurfaceVariant     = SoloMutedForeground,
    primary              = SoloAccent,
    onPrimary            = SoloAccentForeground,
    primaryContainer     = SoloAccentMuted,
    onPrimaryContainer   = SoloAccent,
    secondary            = SoloAccent,
    onSecondary          = SoloAccentForeground,
    tertiary             = SoloAccent,
    onTertiary           = SoloAccentForeground,
    outline              = SoloBorder,
    outlineVariant       = SoloBorder,
    error                = SoloError,
    onError              = SoloForeground,
    errorContainer       = SoloErrorMuted,
    onErrorContainer     = SoloForeground,
    scrim                = SoloBackground,
)

/**
 * Solo's root theme. Dark-only by design.
 *
 * Wraps Material 3 with Solo's color palette, typography, and shape system.
 * Every screen in the app should be a descendant of this theme.
 */
@Composable
fun SoloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoloDarkColors,
        typography  = SoloTypography,
        shapes      = SoloShapes,
        content     = content,
    )
}
