package dev.projectvyuh.solo.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.projectvyuh.solo.core.model.ModelDefinition
import dev.projectvyuh.solo.core.model.ModelInstallState
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloAccentForeground
import dev.projectvyuh.solo.presentation.theme.SoloBackground
import dev.projectvyuh.solo.presentation.theme.SoloBorder
import dev.projectvyuh.solo.presentation.theme.SoloError
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground
import dev.projectvyuh.solo.presentation.theme.SoloSurface

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.DONE) onComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(SoloBackground).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state.step) {
            OnboardingStep.WELCOME     -> WelcomeStep(onContinue = viewModel::advance)
            OnboardingStep.PRIVACY     -> PrivacyStep(onContinue = viewModel::advance)
            OnboardingStep.PERMISSIONS -> PermissionsStep(onContinue = viewModel::advance)
            OnboardingStep.DOWNLOAD    -> DownloadStep(
                model    = viewModel.primaryModel,
                install  = state.install,
                onStart  = viewModel::startModelDownload,
                onFinish = viewModel::advance,
            )
            OnboardingStep.DONE        -> Unit
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text  = "Solo",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = "Your AI agent that lives entirely on your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = SoloMutedForeground,
        )
        Spacer(Modifier.size(8.dp))
        PrimaryButton(text = "Get started", onClick = onContinue)
    }
}

@Composable
private fun PrivacyStep(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text  = "Private by architecture",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        InfoLine("All inference runs on this device. Solo's brain never leaves your phone.")
        InfoLine("The network firewall blocks every outbound request that isn't on the audit allow-list.")
        InfoLine("You can see every network decision Solo makes in the Activity tab.")
        Spacer(Modifier.size(8.dp))
        PrimaryButton(text = "Continue", onClick = onContinue)
    }
}

@Composable
private fun PermissionsStep(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text  = "Permissions",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = "Solo needs notifications to show progress while downloading its model. " +
                "Additional permissions for autonomous action will be requested later, only when needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = SoloMutedForeground,
        )
        // Notification permission request itself happens via the OS; the runtime
        // request is wired in a later phase. For Phase 1A we proceed unconditionally —
        // the foreground service still works without notification permission, it
        // just doesn't show a notification.
        Spacer(Modifier.size(8.dp))
        PrimaryButton(text = "Continue", onClick = onContinue)
    }
}

@Composable
private fun DownloadStep(
    model: ModelDefinition,
    install: ModelInstallState,
    onStart: () -> Unit,
    onFinish: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (install !is ModelInstallState.Downloading && install !is ModelInstallState.Verifying) {
            onStart()
        }
    }

    LaunchedEffect(install) {
        if (install is ModelInstallState.Installed) onFinish()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text  = "Downloading model",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text  = model.displayName + " · " + model.quantization,
            style = MaterialTheme.typography.bodyMedium,
            color = SoloMutedForeground,
        )

        when (val s = install) {
            is ModelInstallState.Downloading -> {
                LinearProgressIndicator(
                    progress  = { s.progressFraction },
                    color     = SoloAccent,
                    trackColor = SoloBorder,
                    modifier  = Modifier.fillMaxWidth(),
                )
                Text(
                    text  = "${formatBytes(s.bytesDownloaded)} / ${formatBytes(s.totalBytes)} · ${formatBytes(s.bytesPerSecond)}/s",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoloMutedForeground,
                )
            }
            is ModelInstallState.PartiallyDownloaded -> {
                Text(
                    text  = "Resuming from ${formatBytes(s.bytesDownloaded)}…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoloMutedForeground,
                )
            }
            ModelInstallState.Verifying -> {
                Text(
                    text  = "Verifying integrity…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoloMutedForeground,
                )
            }
            is ModelInstallState.Failed -> {
                Text(
                    text  = "Download failed: ${s.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoloError,
                )
                TextButton(onClick = onStart) { Text("Retry") }
            }
            is ModelInstallState.Installed -> {
                Text(
                    text  = "Ready",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            ModelInstallState.NotInstalled -> {
                Text(
                    text  = "Preparing…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoloMutedForeground,
                )
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(SoloAccent)
                .padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text  = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors  = ButtonDefaults.buttonColors(
            containerColor = SoloAccent,
            contentColor   = SoloAccentForeground,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000     -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000         -> "%.1f KB".format(bytes / 1_000.0)
    else                   -> "$bytes B"
}
