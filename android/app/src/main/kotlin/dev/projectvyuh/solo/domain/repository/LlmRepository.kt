package dev.projectvyuh.solo.domain.repository

import dev.projectvyuh.solo.core.model.ModelDefinition
import dev.projectvyuh.solo.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

/**
 * Abstract surface the rest of Solo talks to for inference. Hides the fact
 * that the active engine is llama.cpp — eventually we may add MLX (iOS via
 * KMP), an HTA-NPU backend, or a remote-only mode for development.
 *
 * Pure domain interface: no Android, no llama types, no OkHttp.
 */
interface LlmRepository {

    /** True if a model is loaded in memory and ready to generate. */
    val isReady: Boolean

    /** The model currently loaded, or null. */
    val activeModel: ModelDefinition?

    /**
     * Load [model] from disk. The file must already be installed (use
     * ModelManager). Throws if the file is missing or fails to load.
     */
    suspend fun loadModel(model: ModelDefinition)

    /** Release the loaded model. */
    suspend fun unloadModel()

    /**
     * Generate a reply to [conversation]. Emits incremental UTF-8 pieces as the
     * model produces them. The flow completes when generation ends (EOG, max
     * tokens, or cancellation).
     */
    fun generate(conversation: Conversation): Flow<String>

    /** Request mid-stream abort of any active generation. */
    fun abort()
}
