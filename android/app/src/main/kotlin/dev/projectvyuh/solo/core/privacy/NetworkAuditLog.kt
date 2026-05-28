package dev.projectvyuh.solo.core.privacy

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Append-only audit log of every network decision made by [NetworkGuardInterceptor].
 *
 * Format: TSV (tab-separated) — one row per request. Easy to grep, easy to
 * surface in a debug screen, and impossible to confuse with a CSV that
 * might contain a comma in a URL path.
 *
 * Columns: timestamp_iso8601 \t decision \t method \t host \t path \t reason
 *
 * Storage: <filesDir>/audit/network.log, capped at 2 MiB. When the file
 * exceeds the cap, it's rotated to network.log.1 (a single previous
 * generation is retained). This is a non-blocking write path — entries
 * are funneled through a Channel and flushed off the IO dispatcher so
 * the OkHttp interceptor never stalls on disk I/O.
 *
 * NOTE: query strings are intentionally dropped from the recorded path.
 * Tokens, auth signatures, and presigned-URL parameters do not belong
 * in a user-readable log even on the user's own device.
 */
@Singleton
class NetworkAuditLog @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val auditDir = File(context.filesDir, "audit").apply { if (!exists()) mkdirs() }
    private val current  = File(auditDir, "network.log")
    private val rotated  = File(auditDir, "network.log.1")

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    private val channel = Channel<Entry>(capacity = Channel.UNLIMITED)
    private val scope   = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _lastDecision = MutableStateFlow<Entry?>(null)
    /** Reactive view of the most recent log entry — useful for debug screens. */
    val lastDecision: StateFlow<Entry?> = _lastDecision.asStateFlow()

    init {
        scope.launch {
            for (entry in channel) {
                _lastDecision.value = entry
                writeToDisk(entry)
            }
        }
    }

    fun record(decision: NetworkPolicy.Decision) {
        val entry = Entry(
            timestampMs = System.currentTimeMillis(),
            allowed = decision is NetworkPolicy.Decision.Allowed,
            method  = decision.method,
            host    = decision.host,
            path    = decision.path,
            reason  = decision.reason,
        )
        // trySend can fail only if the channel is closed, which only happens at
        // process death; in that case the log entry is lost and that's acceptable.
        channel.trySend(entry)

        // Also mirror to logcat so it shows up in `adb logcat` during dev.
        val tag = if (entry.allowed) "ALLOW" else "BLOCK"
        Log.i(LOGCAT_TAG, "$tag ${entry.method} ${entry.host}${entry.path} :: ${entry.reason}")
    }

    private fun writeToDisk(entry: Entry) {
        try {
            if (current.length() > MAX_BYTES) {
                if (rotated.exists()) rotated.delete()
                current.renameTo(rotated)
            }
            FileOutputStream(current, /*append*/ true).use { out ->
                out.write(formatLine(entry).toByteArray(Charsets.UTF_8))
            }
        } catch (t: Throwable) {
            Log.w(LOGCAT_TAG, "audit write failed", t)
        }
    }

    private fun formatLine(entry: Entry): String {
        val ts = isoFormat.format(Date(entry.timestampMs))
        val decision = if (entry.allowed) "ALLOW" else "BLOCK"
        return "$ts\t$decision\t${entry.method}\t${entry.host}\t${entry.path}\t${entry.reason}\n"
    }

    data class Entry(
        val timestampMs: Long,
        val allowed: Boolean,
        val method: String,
        val host: String,
        val path: String,
        val reason: String,
    )

    companion object {
        private const val LOGCAT_TAG = "NetworkGuard"
        private const val MAX_BYTES  = 2L * 1024 * 1024  // 2 MiB rolling
    }
}
