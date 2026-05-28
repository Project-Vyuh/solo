package dev.projectvyuh.solo.presentation.theme

import androidx.compose.ui.graphics.Color

// -------------------------------------------------------------------------
// Solo color palette — mirrors SOLO-WEBSITE.md §4.2.
// Dark-first by design; light mode arrives in a later phase.
// -------------------------------------------------------------------------

// Surfaces
val SoloBackground       = Color(0xFF09090B)   // app background, near-black
val SoloSurface          = Color(0xFF18181B)   // cards, sheets, elevated
val SoloSurfaceElevated  = Color(0xFF1F1F23)   // one tier above surface
val SoloBorder           = Color(0xFF27272A)   // dividers, card outlines
val SoloBorderStrong     = Color(0xFF3F3F46)   // emphasized borders

// Text
val SoloForeground       = Color(0xFFFAFAFA)   // primary text
val SoloMutedForeground  = Color(0xFFA1A1AA)   // secondary text, metadata
val SoloSubtleForeground = Color(0xFF71717A)   // tertiary, hints

// Accent — violet
val SoloAccent           = Color(0xFFA78BFA)
val SoloAccentForeground = Color(0xFF09090B)
val SoloAccentHover      = Color(0xFFC4B5FD)
val SoloAccentMuted      = Color(0xFF4C1D95)   // accent backgrounds (status pills)

// Semantic
val SoloError            = Color(0xFFEF4444)
val SoloErrorMuted       = Color(0xFF7F1D1D)
val SoloWarning          = Color(0xFFF59E0B)
val SoloSuccess          = Color(0xFF22C55E)
