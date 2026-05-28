package dev.projectvyuh.solo.presentation.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.core.privacy.NetworkAuditLog
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloError
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuditEntryRow(entry: NetworkAuditLog.Entry, modifier: Modifier = Modifier) {
    val badgeColor = if (entry.allowed) SoloAccent else SoloError
    val badgeText  = if (entry.allowed) "ALLOW" else "BLOCK"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text  = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = badgeColor,
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            Text(
                text  = entry.method,
                style = MaterialTheme.typography.labelSmall,
                color = SoloMutedForeground,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 4.dp))
            Text(
                text  = formatTimestamp(entry.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = SoloMutedForeground,
            )
        }
        Text(
            text = entry.host + entry.path,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = entry.reason,
            style = MaterialTheme.typography.bodySmall,
            color = SoloMutedForeground,
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun formatTimestamp(ms: Long): String {
    val now = System.currentTimeMillis()
    val age = now - ms
    return when {
        age < 60_000           -> "${age / 1000}s ago"
        age < 3_600_000        -> "${age / 60_000}m ago"
        age < 24L * 3_600_000  -> timeFormat.format(Date(ms))
        else                   -> dateFormat.format(Date(ms))
    }
}
