package dev.projectvyuh.solo.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// -------------------------------------------------------------------------
// Type scale — mirrors SOLO-WEBSITE.md §4.3.
//
// Font: currently system sans-serif. Drop Inter Variable into
// res/font/inter_variable.ttf and swap [SoloFontFamily] below:
//   val SoloFontFamily = FontFamily(Font(R.font.inter_variable))
// -------------------------------------------------------------------------

private val SoloFontFamily: FontFamily = FontFamily.SansSerif
private val SoloMonoFamily: FontFamily = FontFamily.Monospace

val SoloTypography = Typography(
    // Display (hero) — onboarding welcome
    displayLarge = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp,
    ),

    // Headlines — screen titles
    headlineLarge = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),

    // Titles — section headers, card titles
    titleLarge = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),

    // Body — chat messages, descriptions
    bodyLarge = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,            // 1.6× — optimal for reading
    ),
    bodyMedium = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),

    // Labels — buttons, metadata, status pills
    labelLarge = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SoloFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
)

/** Monospace style for code blocks and inline code. Not part of Material's Typography. */
val SoloCodeStyle = TextStyle(
    fontFamily = SoloMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 22.sp,
)
