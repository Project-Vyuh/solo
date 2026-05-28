package dev.projectvyuh.solo.core.model

import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "SoloArchive"

/**
 * Extracts a `.tar.bz2` archive into a target directory.
 *
 * Used for sherpa-onnx voice model bundles (Moonshine, Kokoro) which ship as
 * tar.bz2 containing model.onnx + tokens.txt + voice/embedding files + eSpeak
 * data.
 *
 * Implementation: Apache Commons Compress for the tar reader,
 * BZip2CompressorInputStream for the bzip2 decompressor. Standard combination,
 * no JNI surprises.
 *
 * Path-traversal protection: refuses to write any entry whose resolved path
 * escapes [targetDir]. Required for security since archive sources are
 * downloaded from the network (HTTPS-checked but still untrusted).
 */
object ArchiveExtractor {

    /**
     * Extract [archive] into [targetDir]. Existing files at conflicting paths
     * are overwritten. Returns the total number of bytes written.
     */
    fun extractTarBz2(archive: File, targetDir: File): Long {
        require(archive.exists()) { "archive does not exist: $archive" }
        if (!targetDir.exists()) targetDir.mkdirs()

        var totalBytesWritten = 0L
        var entryCount = 0
        val canonicalTarget = targetDir.canonicalFile

        BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive))).use { bz ->
            TarArchiveInputStream(bz).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val out = File(targetDir, entry.name).canonicalFile
                    require(out.toPath().startsWith(canonicalTarget.toPath())) {
                        "archive entry escapes target dir: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos ->
                            val buf = ByteArray(1 shl 16)
                            while (true) {
                                val n = tar.read(buf); if (n < 0) break
                                fos.write(buf, 0, n)
                                totalBytesWritten += n
                            }
                        }
                    }
                    entryCount++
                    entry = tar.nextEntry
                }
            }
        }
        Log.i(TAG, "extracted ${archive.name}: $entryCount entries, $totalBytesWritten bytes → $targetDir")
        return totalBytesWritten
    }
}
