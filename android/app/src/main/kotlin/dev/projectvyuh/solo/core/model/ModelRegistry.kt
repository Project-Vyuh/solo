package dev.projectvyuh.solo.core.model

/**
 * Static catalogue of models Solo can download.
 *
 * Adding a model: drop in a [ModelDefinition] with the verified sha256 from
 * Hugging Face's `x-linked-etag` header (which is the upstream git-LFS SHA-256).
 * That hash MUST match the actual file content; mismatch aborts installation.
 *
 * Solo's primary model is Gemma 3n E4B — the mobile-optimized natively
 * multimodal model from Google DeepMind (text + image + audio + video in,
 * text out). E4B = ~4B effective parameters with Per-Layer Embedding (PLE)
 * selective activation, designed specifically for on-device inference.
 */
object ModelRegistry {

    val GEMMA_3N_E4B_Q4_K_M = ModelDefinition(
        id              = "gemma-3n-E4B-it-Q4_K_M",
        displayName     = "Gemma 3n E4B",
        description     = "Google DeepMind's mobile-first multimodal model. Text, image, audio, video in; text out. 4B effective params via Per-Layer Embedding.",
        downloadUrl     = "https://huggingface.co/unsloth/gemma-3n-E4B-it-GGUF/resolve/main/gemma-3n-E4B-it-Q4_K_M.gguf",
        sha256          = "43b489bb77a81bda85180e7c490d40ad7f1d5c2ce654c9b05e15e104bd3c777e",
        sizeBytes       = 4_539_054_208L,
        parameterCount  = "4B effective (PLE)",
        contextWindow   = 32_768,
        quantization    = "Q4_K_M",
        chatTemplate    = ChatTemplate.GEMMA,
        isMultimodal    = true,
    )

    /** All models the app knows about, in display order. */
    val all: List<ModelDefinition> = listOf(
        GEMMA_3N_E4B_Q4_K_M,
    )

    val primary: ModelDefinition = GEMMA_3N_E4B_Q4_K_M

    fun byId(id: String): ModelDefinition? = all.firstOrNull { it.id == id }
}
