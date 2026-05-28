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
    val chatTemplate: ChatTemplate,
    val isMultimodal: Boolean = false,
) {
    /** The on-disk filename used when the GGUF is fully downloaded and verified. */
    val fileName: String get() = "$id.gguf"

    /** Partial download filename — atomically renamed to [fileName] on SHA-256 success. */
    val partialFileName: String get() = "$id.gguf.part"
}

/**
 * Chat template format. Determines how a conversation is serialized into the
 * single string the model sees during inference. Each model family ships
 * tokenizer-level special tokens that map to one of these formats.
 */
enum class ChatTemplate {
    /** Gemma 4 (March 2026+) — new control tokens: `<|turn>role\n...<turn|>`. */
    GEMMA4,
    /** Gemma 3 family — `<start_of_turn>role\n...<end_of_turn>`. */
    GEMMA3,
    /** OpenAI / Qwen ChatML: `<|im_start|>role\n...<|im_end|>` */
    CHATML,
    /** Meta Llama 3: `<|start_header_id|>role<|end_header_id|>\n...<|eot_id|>` */
    LLAMA3,
    /** Microsoft Phi: `<|system|>\n...<|end|>\n<|user|>\n...<|end|>\n<|assistant|>\n` */
    PHI,
}
