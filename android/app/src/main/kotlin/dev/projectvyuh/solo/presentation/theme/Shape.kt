package dev.projectvyuh.solo.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Mirrors SOLO-WEBSITE.md §4.4 corner radii:
//   cards     → 8dp
//   pills     → 6dp (full rounded for status pills)
//   inline    → 4dp
val SoloShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // inline code, tiny chips
    small      = RoundedCornerShape(6.dp),   // pills (when paired with `RoundedCornerShape(50%)` for full pills)
    medium     = RoundedCornerShape(8.dp),   // cards, message bubbles, sheets
    large      = RoundedCornerShape(12.dp),  // larger surfaces, dialogs
    extraLarge = RoundedCornerShape(16.dp),  // bottom sheets
)
