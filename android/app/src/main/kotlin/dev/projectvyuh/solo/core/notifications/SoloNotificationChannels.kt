package dev.projectvyuh.solo.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Centralized notification channel definitions.
 *
 * Phase 1A only needs the model-download channel (foreground service while
 * downloading the 4 GB GGUF). Later phases will add channels for proactive
 * agent suggestions, autonomous actions, and alarms.
 */
object SoloNotificationChannels {

    const val MODEL_DOWNLOAD_ID = "solo.model_download"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val downloadChannel = NotificationChannel(
            MODEL_DOWNLOAD_ID,
            "Model download",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progress notification while Solo downloads its on-device model."
            setShowBadge(false)
        }
        mgr.createNotificationChannel(downloadChannel)
    }
}
