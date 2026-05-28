package dev.projectvyuh.solo.core.model

/**
 * Immutable description of a model Solo knows how to download and run.
 *
 * Entries live in [ModelRegistry]. Each model is identified by a stable [id]
 * (used as the on-disk filename) and verified post-download against [sha256].
 */
data class ModelDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val downloadUrl: String,
    /**
     * SHA-256 of the downloaded artifact, lowercase hex. Empty string disables
     * verification for models whose authoritative hash we don't have (e.g.,
     * GitHub release files that lack a published manifest); HTTPS + size check
     * still apply.
     */
    val sha256: String,
    val sizeBytes: Long,
    val parameterCount: String,
    val contextWindow: Int,
    val quantization: String,
    val format: ModelFormat,
    val isMultimodal: Boolean = false,
) {
    /** The on-disk filename used when the model is fully downloaded and verified. */
    val fileName: String get() = "$id.${format.extension}"

    /** Partial download filename — atomically renamed to [fileName] on SHA-256 success. */
    val partialFileName: String get() = "$id.${format.extension}.part"

    /**
     * For archive formats, the directory the contents are extracted into
     * (next to the archive). The voice engines load files from here.
     */
    val extractDirName: String get() = id
}

/**
 * The runtime model format. Each format implies a different inference engine
 * (or in the voice case, a different post-download handling).
 *
 * Single-file formats are downloaded then mmap'd directly:
 * - [LITERTLM]: Google AI Edge LiteRT-LM `.litertlm` files
 * - [GGUF]:     Legacy llama.cpp format. Kept in the enum for institutional
 *               history but no longer used by Solo as of 2026-05-28.
 * - [ONNX]:     Single-file ONNX model (Silero VAD)
 *
 * Archive formats are downloaded, SHA-256-verified, then extracted into a
 * directory; the engine consumes individual files from that directory:
 * - [SHERPA_TAR_BZ2]: tar.bz2 bundle published by k2-fsa/sherpa-onnx for
 *                     Moonshine STT and Kokoro TTS. Contains model.onnx,
 *                     tokens.txt, voice/embedding files, eSpeak data, etc.
 */
enum class ModelFormat(val extension: String, val isArchive: Boolean) {
    LITERTLM("litertlm", false),
    GGUF("gguf", false),
    ONNX("onnx", false),
    SHERPA_TAR_BZ2("tar.bz2", true),
}
