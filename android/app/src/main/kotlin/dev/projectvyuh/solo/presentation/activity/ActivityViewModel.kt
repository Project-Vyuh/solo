package dev.projectvyuh.solo.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.projectvyuh.solo.core.privacy.NetworkAuditLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class ActivityUiState(
    val entries: List<NetworkAuditLog.Entry> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Reads the persisted audit log file ([NetworkAuditLog]) and exposes its
 * entries in reverse-chronological order for the Activity screen.
 *
 * Also subscribes to [NetworkAuditLog.lastDecision] so the screen updates
 * live as new requests flow through the guard.
 */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val auditLog: NetworkAuditLog,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        refresh()
        // Live updates: re-read the file whenever a new decision is recorded.
        viewModelScope.launch {
            auditLog.lastDecision.collect { _ ->
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { readLog() }
            _state.update { it.copy(entries = entries, isLoading = false) }
        }
    }

    private fun readLog(): List<NetworkAuditLog.Entry> {
        val file = File(context.filesDir, "audit/network.log")
        if (!file.exists()) return emptyList()
        return file.useLines { lines ->
            lines.mapNotNull(::parseLine).toList()
        }.reversed()
    }

    private fun parseLine(line: String): NetworkAuditLog.Entry? {
        // TSV: timestamp \t decision \t method \t host \t path \t reason
        val cols = line.split('\t')
        if (cols.size < 6) return null
        return try {
            NetworkAuditLog.Entry(
                timestampMs = ISO_FORMAT.parse(cols[0])?.time ?: return null,
                allowed     = cols[1] == "ALLOW",
                method      = cols[2],
                host        = cols[3],
                path        = cols[4],
                reason      = cols[5],
            )
        } catch (_: Exception) { null }
    }

    companion object {
        private val ISO_FORMAT = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US
        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    }
}
