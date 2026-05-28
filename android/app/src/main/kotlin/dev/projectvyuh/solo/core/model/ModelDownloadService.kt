package dev.projectvyuh.solo.core.model

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.projectvyuh.solo.MainActivity
import dev.projectvyuh.solo.core.notifications.SoloNotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that performs the GGUF download.
 *
 * Solo can't ship its capability with an ~4 GB blob inside the APK — it
 * has to be downloaded on first launch. That download is 5-15 minutes on
 * typical mobile networks, which is way longer than a backgrounded
 * non-foreground task can survive on modern Android.
 *
 * The service:
 *   - Posts a sticky low-importance notification with live progress
 *   - Streams [ModelInstallState] back through [ModelDownloadController]
 *   - On success, loads the model into the inference engine and exits
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {

    @Inject lateinit var modelManager: ModelManager
    @Inject lateinit var controller: ModelDownloadController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private val notificationId = 0x501C  // arbitrary stable ID

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SoloNotificationChannels.ensureCreated(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
        val model = modelId?.let { ModelRegistry.byId(it) }
        if (model == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground(buildNotification(0, model.sizeBytes, "Starting…"))

        if (downloadJob?.isActive == true) return START_NOT_STICKY

        downloadJob = scope.launch {
            modelManager.install(model).collect { state ->
                controller.updateState(state)
                when (state) {
                    is ModelInstallState.Downloading -> {
                        updateNotification(
                            current = state.bytesDownloaded,
                            total   = state.totalBytes,
                            status  = "${formatBytes(state.bytesPerSecond)}/s",
                        )
                    }
                    ModelInstallState.Verifying -> {
                        updateNotification(model.sizeBytes, model.sizeBytes, "Verifying integrity…")
                    }
                    is ModelInstallState.Installed -> {
                        updateNotification(model.sizeBytes, model.sizeBytes, "Loading into engine…")
                        controller.loadIntoEngine(model)
                        controller.updateState(state)   // republish so observers see final state
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    is ModelInstallState.Failed -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    else -> { /* states with no notification update */ }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun updateNotification(current: Long, total: Long, status: String) {
        val n = buildNotification(current, total, status)
        val mgr = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        mgr.notify(notificationId, n)
    }

    private fun buildNotification(current: Long, total: Long, status: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val percent = if (total > 0) ((current * 100) / total).toInt() else 0
        return NotificationCompat.Builder(this, SoloNotificationChannels.MODEL_DOWNLOAD_ID)
            .setContentTitle("Downloading Solo's model")
            .setContentText("$status · ${formatBytes(current)} / ${formatBytes(total)}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, total <= 0)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val EXTRA_MODEL_ID = "model_id"
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000     -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000         -> "%.1f KB".format(bytes / 1_000.0)
    else                   -> "$bytes B"
}
