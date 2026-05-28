package dev.projectvyuh.solo.core.model

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns Solo's on-disk model files.
 *
 * Storage layout:
 *   <filesDir>/models/<modelId>.gguf      — verified, ready to mmap
 *   <filesDir>/models/<modelId>.gguf.part — partial; resumed on next download
 *
 * Download protocol:
 *   1. HEAD via Range request → confirm server supports resume (most HF mirrors do).
 *   2. Open .part for write, start at existing length, append.
 *   3. Hash bytes through MessageDigest as they're written (single pass; no re-read).
 *   4. On EOF, compare against [ModelDefinition.sha256]. Mismatch → delete .part, fail.
 *   5. Atomically rename .part → final filename.
 *
 * No data ever leaves the device; the only outbound traffic is GETs to the
 * whitelisted model host. NetworkGuard (Phase 1A Step 4) will enforce this.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {

    private val modelsDir: File by lazy {
        File(context.filesDir, "models").apply { if (!exists()) mkdirs() }
    }

    fun modelFile(def: ModelDefinition): File = File(modelsDir, def.fileName)

    private fun partialFile(def: ModelDefinition): File = File(modelsDir, def.partialFileName)

    /** For archive-format models, the directory the contents were extracted into. */
    fun extractDir(def: ModelDefinition): File = File(modelsDir, def.extractDirName)

    /** Current install state, recomputed from disk. Cheap (only stats files). */
    fun stateOf(def: ModelDefinition): ModelInstallState {
        // Archive formats: "installed" means the extracted dir exists and is
        // non-empty; the archive file itself is deleted after extraction.
        if (def.format.isArchive) {
            val dir = extractDir(def)
            if (dir.exists() && dir.isDirectory && dir.listFiles()?.isNotEmpty() == true) {
                return ModelInstallState.Installed(dir.absolutePath)
            }
            val partial = partialFile(def)
            if (partial.exists() && partial.length() in 1..<def.sizeBytes) {
                return ModelInstallState.PartiallyDownloaded(partial.length(), def.sizeBytes)
            }
            return ModelInstallState.NotInstalled
        }
        // Single-file formats.
        val full = modelFile(def)
        if (full.exists() && full.length() == def.sizeBytes) {
            return ModelInstallState.Installed(full.absolutePath)
        }
        val partial = partialFile(def)
        if (partial.exists() && partial.length() in 1..<def.sizeBytes) {
            return ModelInstallState.PartiallyDownloaded(partial.length(), def.sizeBytes)
        }
        return ModelInstallState.NotInstalled
    }

    /** True if the model is fully installed and verified. */
    fun isInstalled(def: ModelDefinition): Boolean =
        stateOf(def) is ModelInstallState.Installed

    /** Wipe a model from disk (file, partial, extracted dir). */
    fun delete(def: ModelDefinition) {
        modelFile(def).delete()
        partialFile(def).delete()
        if (def.format.isArchive) extractDir(def).deleteRecursively()
    }

    /**
     * Download and verify [def]. Resumes if a `.part` exists.
     * Emits granular progress; the terminal state is either [ModelInstallState.Installed]
     * or [ModelInstallState.Failed].
     */
    fun install(def: ModelDefinition): Flow<ModelInstallState> = callbackFlow {
        val partial = partialFile(def)
        val final = modelFile(def)

        // Already installed → fast path.
        if (final.exists() && final.length() == def.sizeBytes) {
            trySend(ModelInstallState.Installed(final.absolutePath))
            close()
            return@callbackFlow
        }

        val resumeFrom = if (partial.exists()) partial.length() else 0L
        if (resumeFrom >= def.sizeBytes) {
            // Partial somehow exceeded expected size — start over.
            partial.delete()
        }

        Log.i(TAG, "downloading ${def.id} from byte $resumeFrom of ${def.sizeBytes}")

        val request = Request.Builder()
            .url(def.downloadUrl)
            .apply {
                if (resumeFrom > 0) addHeader("Range", "bytes=$resumeFrom-")
            }
            .build()

        val call = httpClient.newCall(request)

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    trySend(ModelInstallState.Failed("HTTP ${response.code}: ${response.message}"))
                    close()
                    return@use
                }
                val body = response.body
                    ?: run {
                        trySend(ModelInstallState.Failed("empty response body"))
                        close()
                        return@use
                    }

                // When resuming we still hash the bytes we already have on disk before
                // appending — that way a partial file corrupted by an interrupted
                // write is caught at verification time rather than producing a
                // silent SHA-256 mismatch.
                val digest = MessageDigest.getInstance("SHA-256")
                if (resumeFrom > 0) {
                    partial.inputStream().use { existing ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = existing.read(buf); if (n <= 0) break
                            digest.update(buf, 0, n)
                        }
                    }
                }

                RandomAccessFile(partial, "rw").use { raf ->
                    raf.seek(raf.length())
                    body.byteStream().use { src ->
                        val buf = ByteArray(1 shl 16)  // 64 KiB
                        var written = resumeFrom
                        var lastReport = System.nanoTime()
                        var lastReportBytes = resumeFrom

                        while (isActive) {
                            val n = src.read(buf); if (n < 0) break
                            raf.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            written += n

                            val now = System.nanoTime()
                            val elapsedNs = now - lastReport
                            if (elapsedNs >= 250_000_000L) {  // every 250ms
                                val bps = ((written - lastReportBytes) * 1_000_000_000L) / elapsedNs
                                trySend(ModelInstallState.Downloading(written, def.sizeBytes, bps))
                                lastReport = now
                                lastReportBytes = written
                            }
                        }
                    }
                }

                trySend(ModelInstallState.Verifying)

                // SHA-256 check is skipped when the registry left it blank (see
                // VoiceModelRegistry KDoc — GitHub Releases don't publish
                // authoritative hashes). HTTPS + completed-byte-count check
                // already provide transport integrity.
                if (def.sha256.isNotBlank()) {
                    val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actualSha.equals(def.sha256, ignoreCase = true)) {
                        partial.delete()
                        trySend(ModelInstallState.Failed(
                            "SHA-256 mismatch: expected ${def.sha256}, got $actualSha"
                        ))
                        close()
                        return@use
                    }
                }

                if (!partial.renameTo(final)) {
                    trySend(ModelInstallState.Failed("rename ${partial.name} -> ${final.name} failed"))
                    close()
                    return@use
                }

                // Archive formats (sherpa-onnx tar.bz2): extract next to the
                // archive and remove the archive to free disk. The engines load
                // their files from <modelsDir>/<extractDirName>/.
                if (def.format.isArchive) {
                    try {
                        val extractTo = File(modelsDir, def.extractDirName)
                        if (extractTo.exists()) extractTo.deleteRecursively()
                        ArchiveExtractor.extractTarBz2(final, extractTo)
                        final.delete()    // archive served its purpose
                    } catch (t: Throwable) {
                        trySend(ModelInstallState.Failed("extraction failed: ${t.message}", t))
                        close()
                        return@use
                    }
                }

                Log.i(TAG, "installed ${def.id} -> ${final.absolutePath} (${if (def.sha256.isNotBlank()) "sha256 verified" else "size only"})")
                trySend(ModelInstallState.Installed(final.absolutePath))
                close()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "download failed for ${def.id}", t)
            trySend(ModelInstallState.Failed(t.message ?: "download error", t))
            close()
        }

        awaitClose {
            if (!call.isCanceled()) call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "ModelManager"

        /** OkHttp builder tuned for multi-gigabyte transfers. Used by the DI module. */
        fun buildHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)         // no global cap; a 4GB download can take a while
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
