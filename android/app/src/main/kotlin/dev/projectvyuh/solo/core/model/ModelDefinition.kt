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
}

/**
 * The runtime model format. Each format implies a different inference engine.
 *
 * - [LITERTLM]: Google AI Edge LiteRT-LM `.litertlm` files. Tokenizer, weights,
 *               and chat template are bundled. The engine handles formatting
 *               internally; we do NOT format prompts manually.
 * - [GGUF]:     Legacy llama.cpp format. Kept in the enum for institutional
 *               history but no longer used by Solo as of 2026-05-28.
 */
enum class ModelFormat(val extension: String) {
    LITERTLM("litertlm"),
    GGUF("gguf"),
}
