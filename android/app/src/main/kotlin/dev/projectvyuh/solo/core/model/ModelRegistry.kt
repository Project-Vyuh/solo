package dev.projectvyuh.solo.core.model

/**
 * Static catalogue of models Solo can download.
 *
 * Adding a model: drop in a [ModelDefinition] with the verified sha256 from
 * Hugging Face's `x-linked-etag` header (which is the upstream git-LFS SHA-256).
 * That hash MUST match the actual file content; mismatch aborts installation.
 *
 * Phase 1A primary: **Gemma 4 E2B** in LiteRT-LM `.litertlm` format. Google's
 * official on-device runtime for Gemma 3n / Gemma 4 is LiteRT-LM (the same
 * runtime powering Gemini Nano in Chrome, Pixel Watch Smart Replies, etc.).
 *
 * E2B (~2B effective params via Per-Layer Embedding) was chosen over E4B for
 * Phase 1A because it fits comfortably on mid-tier devices like Galaxy A36
 * (Snapdragon 6 Gen 3, 8 GB RAM). E4B remains a planned opt-in for flagship
 * devices once device-tier detection ships in Phase 1B.
 *
 * See SOLO-LITERT-MIGRATION.md for the full rationale and runtime swap history.
 */
object ModelRegistry {

    val GEMMA_4_E2B_LITERTLM = ModelDefinition(
        id              = "gemma-4-E2B-it",
        displayName     = "Gemma 4 E2B",
        description     = "Google DeepMind's mobile-first multimodal model in " +
                          "Google AI Edge LiteRT-LM format. 2B effective params " +
                          "via Per-Layer Embedding. Text, image, audio inputs.",
        downloadUrl     = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sha256          = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        sizeBytes       = 2_588_147_712L,
        parameterCount  = "2B effective (PLE)",
        contextWindow   = 32_768,
        quantization    = "int4 (LiteRT)",
        format          = ModelFormat.LITERTLM,
        isMultimodal    = true,
    )

    /** All models the app knows about, in display order. */
    val all: List<ModelDefinition> = listOf(
        GEMMA_4_E2B_LITERTLM,
    )

    val primary: ModelDefinition = GEMMA_4_E2B_LITERTLM

    fun byId(id: String): ModelDefinition? = all.firstOrNull { it.id == id }
}
