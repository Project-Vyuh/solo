package dev.projectvyuh.solo.presentation.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloBorder
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground

/**
 * First-tap-to-speak dialog. Asks the user to opt in to the ~400 MB voice
 * model download (Moonshine + Silero + Kokoro). Closes itself when the
 * download progresses to Installed.
 */
@Composable
fun VoiceDownloadDialog(
    state: VoiceDownloadDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable voice") },
        text = {
            Column {
                Text(
                    text = when (state) {
                        is VoiceDownloadDialogState.Confirming ->
                            "Solo's voice runtime needs three open-source models: " +
                            "Moonshine (speech-to-text), Silero VAD, and Kokoro " +
                            "(text-to-speech). Total ~400 MB. All inference stays on " +
                            "your device — no audio leaves your phone."
                        is VoiceDownloadDialogState.Downloading ->
                            "Downloading ${state.currentModel} " +
                            "(${state.downloadedMb} MB / ${state.totalMb} MB)…"
                        is VoiceDownloadDialogState.Failed ->
                            "Download failed: ${state.message}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state is VoiceDownloadDialogState.Downloading) {
                    LinearProgressIndicator(
                        progress  = { state.fraction },
                        color     = SoloAccent,
                        trackColor = SoloBorder,
                        modifier  = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text  = "Keep the app open. Solo will be ready to speak when " +
                                "this completes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoloMutedForeground,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                is VoiceDownloadDialogState.Confirming ->
                    TextButton(onClick = onConfirm) { Text("Download") }
                is VoiceDownloadDialogState.Failed ->
                    TextButton(onClick = onConfirm) { Text("Retry") }
                is VoiceDownloadDialogState.Downloading -> Unit
            }
        },
        dismissButton = {
            if (state !is VoiceDownloadDialogState.Downloading) {
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        },
    )
}

sealed interface VoiceDownloadDialogState {
    object Confirming : VoiceDownloadDialogState
    data class Downloading(
        val currentModel: String,
        val downloadedMb: Long,
        val totalMb: Long,
        val fraction: Float,
    ) : VoiceDownloadDialogState
    data class Failed(val message: String) : VoiceDownloadDialogState
}
