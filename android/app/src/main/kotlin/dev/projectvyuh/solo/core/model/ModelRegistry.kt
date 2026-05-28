package dev.projectvyuh.solo.core.model

/**
 * Static catalogue of models Solo can download.
 *
 * Adding a model: drop in a [ModelDefinition] with the verified sha256 from
 * Hugging Face's `x-linked-etag` header (which is the upstream git-LFS SHA-256).
 * That hash MUST match the actual file content; mismatch aborts installation.
 *
 * Solo's primary model is Gemma 4 E4B — Google DeepMind's March 2026
 * mobile-optimized multimodal frontier model. Natively multimodal
 * (text + image + audio + video → text), 4B effective parameters with
 * Per-Layer Embedding selective activation, and a built-in `<|think|>`
 * thinking mode token trained into the model from scratch.
 */
object ModelRegistry {

    val GEMMA_4_E4B_Q4_K_M = ModelDefinition(
        id              = "gemma-4-E4B-it-Q4_K_M",
        displayName     = "Gemma 4 E4B",
        description     = "Google DeepMind's mobile-first multimodal frontier model. " +
                          "Text, image, audio, video in; text out. 4B effective params " +
                          "via Per-Layer Embedding. Native thinking-mode token.",
        downloadUrl     = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf",
        sha256          = "519b9793ed6ce0ff530f1b7c96e848e08e49e7af4d57bb97f76215963a54146d",
        sizeBytes       = 4_977_169_568L,
        parameterCount  = "4B effective (PLE)",
        contextWindow   = 32_768,
        quantization    = "Q4_K_M",
        chatTemplate    = ChatTemplate.GEMMA4,
        isMultimodal    = true,
    )

    /** All models the app knows about, in display order. */
    val all: List<ModelDefinition> = listOf(
        GEMMA_4_E4B_Q4_K_M,
    )

    val primary: ModelDefinition = GEMMA_4_E4B_Q4_K_M

    fun byId(id: String): ModelDefinition? = all.firstOrNull { it.id == id }
}
