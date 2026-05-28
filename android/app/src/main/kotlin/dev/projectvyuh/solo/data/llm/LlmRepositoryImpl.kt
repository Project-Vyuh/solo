package dev.projectvyuh.solo.data.llm

import dev.projectvyuh.solo.core.model.ModelDefinition
import dev.projectvyuh.solo.core.model.ModelManager
import dev.projectvyuh.solo.domain.model.Conversation
import dev.projectvyuh.solo.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs [LlmRepository] with [LlamaCppEngine].
 *
 * Responsibilities:
 *   - Resolve the GGUF path via [ModelManager] (refuses to load a model that
 *     isn't fully installed and verified).
 *   - Apply the correct chat template for the active model.
 *   - Pick sensible default sampling params per request.
 */
@Singleton
class LlmRepositoryImpl @Inject constructor(
    private val engine: LlamaCppEngine,
    private val modelManager: ModelManager,
) : LlmRepository {

    @Volatile private var loaded: ModelDefinition? = null

    override val isReady: Boolean get() = engine.isLoaded
    override val activeModel: ModelDefinition? get() = loaded

    override suspend fun loadModel(model: ModelDefinition) {
        check(modelManager.isInstalled(model)) {
            "model ${model.id} is not installed; download it via ModelManager first"
        }
        val path = modelManager.modelFile(model).absolutePath
        // Threading: pick conservative defaults. On flagship Snapdragon 8 Gen 3+
        // we have 4-8 perf cores. 4 threads is a safe default that avoids
        // saturating little cores (which would thrash thermals).
        engine.load(
            path        = path,
            contextSize = model.contextWindow.coerceAtMost(8192),  // start moderate; can raise later
            threadCount = 4,
            gpuLayers   = 0,
        )
        loaded = model
    }

    override suspend fun unloadModel() {
        engine.unload()
        loaded = null
    }

    override fun generate(conversation: Conversation): Flow<String> {
        val model = loaded ?: error("no model loaded")
        val prompt = ChatTemplateFormatter.format(conversation, model.chatTemplate)
        return engine.complete(prompt, SamplingParams())
    }

    override fun abort() = engine.abort()
}
