package dev.projectvyuh.solo.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.projectvyuh.solo.core.thermal.ThermalMonitor
import dev.projectvyuh.solo.presentation.theme.SoloBackground
import dev.projectvyuh.solo.presentation.theme.SoloBorder
import dev.projectvyuh.solo.presentation.theme.SoloError
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground
import dev.projectvyuh.solo.presentation.theme.SoloSuccess
import dev.projectvyuh.solo.presentation.theme.SoloSurface
import dev.projectvyuh.solo.presentation.theme.SoloWarning

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SoloBackground),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text  = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        item {
            SettingCard(title = "Model") {
                state.activeModel?.let { model ->
                    InfoRow("Name", model.displayName)
                    InfoRow("Quantization", model.quantization)
                    InfoRow("Context window", "${model.contextWindow} tokens")
                    InfoRow(
                        "On disk",
                        if (state.modelInstalled) "${formatBytes(state.modelSizeBytes)} (installed)"
                        else "not installed",
                    )
                }
            }
        }

        item {
            SettingCard(title = "Privacy") {
                Text(
                    text = "Solo's network firewall blocks every outbound request " +
                        "that isn't on the audit allow-list. See the Activity tab " +
                        "for the live log.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoloMutedForeground,
                )
            }
        }

        item {
            SettingCard(title = "Device") {
                val color = when (state.thermal) {
                    ThermalMonitor.Level.NONE, ThermalMonitor.Level.LIGHT -> SoloSuccess
                    ThermalMonitor.Level.MODERATE -> SoloWarning
                    else -> SoloError
                }
                InfoRow("Thermal", state.thermal.displayName, valueColor = color)
            }
        }

        item {
            SettingCard(title = "Build") {
                InfoRow("Inference backend", state.backend.name)
                InfoRow("App", "0.0.1")
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoloSurface, MaterialTheme.shapes.medium)
            .border(1.dp, SoloBorder, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box { content() }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = SoloMutedForeground,
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000     -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000         -> "%.1f KB".format(bytes / 1_000.0)
    else                   -> "$bytes B"
}
