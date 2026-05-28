package dev.projectvyuh.solo.presentation.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

// -------------------------------------------------------------------------
// Motion tokens — centralized so streaming/screen-transition/UI feedback
// feel coherent across the app.
//
// Easings follow Material 3 emphasis curves (sharp arrival, gentle exit).
// -------------------------------------------------------------------------

object SoloMotion {

    // Durations (ms)
    const val InstantMs   = 80    // ripples, button press
    const val FastMs      = 150   // bottom-sheet, scrim, tab swap
    const val MediumMs    = 250   // most screen transitions
    const val SlowMs      = 400   // first-load, onboarding reveals
    const val VerySlowMs  = 600   // hero animations, success states

    // Cursor blink: long enough to feel alive, short enough not to distract.
    const val StreamingCursorBlinkMs = 530

    // Easings
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val Standard:             Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerate:   Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)

    // Pre-built tween specs for common cases
    val FastFade get() = tween<Float>(durationMillis = FastMs, easing = Standard)
    val ScreenEnter get() = tween<Float>(durationMillis = MediumMs, easing = EmphasizedDecelerate)
    val ScreenExit  get() = tween<Float>(durationMillis = FastMs,  easing = EmphasizedAccelerate)
}
