package dev.projectvyuh.solo.core.thermal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the device's thermal status from [PowerManager].
 *
 * Phase 1A: surfaces thermal state in the settings/debug UI.
 * Future phases: used to throttle inference (reduce n_threads, lower context
 * limit, prefer the smaller draft model) when the SoC is hot.
 *
 * Available on API 29+. On older Android, emits [THERMAL_STATUS_NONE].
 */
@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /** Snapshot of the current thermal level. Names map to PowerManager constants. */
    enum class Level(val rawCode: Int, val displayName: String) {
        NONE(0, "Normal"),
        LIGHT(1, "Light"),
        MODERATE(2, "Moderate"),
        SEVERE(3, "Severe"),
        CRITICAL(4, "Critical"),
        EMERGENCY(5, "Emergency"),
        SHUTDOWN(6, "Shutdown");

        companion object {
            fun fromRaw(code: Int): Level = entries.firstOrNull { it.rawCode == code } ?: NONE
        }
    }

    fun currentLevel(): Level =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Level.fromRaw(powerManager.currentThermalStatus)
        } else Level.NONE

    /** Live thermal events. On API < 29, emits a single NONE value. */
    fun observe(): Flow<Level> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callbackFlow {
                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    trySend(Level.fromRaw(status))
                }
                trySend(Level.fromRaw(powerManager.currentThermalStatus))
                powerManager.addThermalStatusListener(listener)
                awaitClose { powerManager.removeThermalStatusListener(listener) }
            }
        } else flowOf(Level.NONE)
}
