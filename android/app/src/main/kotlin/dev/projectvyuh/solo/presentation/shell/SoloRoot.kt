package dev.projectvyuh.solo.presentation.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.projectvyuh.solo.presentation.onboarding.OnboardingScreen
import dev.projectvyuh.solo.presentation.shell.gating.SoloRootViewModel

/**
 * Top of the Compose tree. Picks between the onboarding flow and the main
 * navigation shell based on whether the primary model is installed.
 */
@Composable
fun SoloRoot(viewModel: SoloRootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var manualAdvance by remember { mutableStateOf(false) }

    val showMain = manualAdvance || state.modelReady

    if (showMain) {
        SoloAppRoot()
    } else {
        OnboardingScreen(onComplete = {
            manualAdvance = true
            viewModel.markOnboardingComplete()
        })
    }
}
