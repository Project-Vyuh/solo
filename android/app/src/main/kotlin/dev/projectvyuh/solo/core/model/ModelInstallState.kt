package dev.projectvyuh.solo.core.model

/** Lifecycle states for one model on this device. */
sealed interface ModelInstallState {
    object NotInstalled : ModelInstallState

    /** A partial download exists on disk; can resume from [bytesDownloaded]. */
    data class PartiallyDownloaded(
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : ModelInstallState

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : ModelInstallState {
        val progressFraction: Float
            get() = if (totalBytes <= 0) 0f else bytesDownloaded.toFloat() / totalBytes
    }

    /** Download finished; SHA-256 verification in progress. */
    object Verifying : ModelInstallState

    /** Installed and verified. The file at [absolutePath] is ready for mmap. */
    data class Installed(val absolutePath: String) : ModelInstallState

    data class Failed(val message: String, val cause: Throwable? = null) : ModelInstallState
}
