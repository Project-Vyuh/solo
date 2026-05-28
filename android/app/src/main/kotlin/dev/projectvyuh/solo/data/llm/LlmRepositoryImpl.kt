package dev.projectvyuh.solo.data.llm

import dev.projectvyuh.solo.core.model.ModelDefinition
import dev.projectvyuh.solo.core.model.ModelManager
import dev.projectvyuh.solo.domain.model.Conversation
import dev.projectvyuh.solo.domain.model.Role
import dev.projectvyuh.solo.domain.persona.SoloSystemPrompt
import dev.projectvyuh.solo.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs [LlmRepository] with [LiteRtLmEngine].
 *
 * Key differences vs the prior llama.cpp implementation:
 *  - No prompt formatting here. LiteRT-LM's `Conversation` accepts the user's
 *    raw message and applies the model's bundled chat template internally.
 *    Our [SoloSystemPrompt] becomes the `systemInstruction` on the
 *    Conversation at load time.
 *  - Multi-turn context (the running KV cache) lives inside LiteRT-LM's
 *    Conversation, so we only forward the *latest* user message — not the
 *    full Conversation domain object.
 *  - Sampling params are configured once on the Conversation; per-call
 *    parameter overrides are deferred to a future API surface.
 */
@Singleton
class LlmRepositoryImpl @Inject constructor(
    private val engine: LiteRtLmEngine,
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
        engine.load(
            modelPath         = path,
            preferredBackend  = LiteRtLmEngine.BackendType.GPU,
            systemInstruction = SoloSystemPrompt.build(),
        )
        loaded = model
    }

    override suspend fun unloadModel() {
        engine.unload()
        loaded = null
    }

    override fun generate(conversation: Conversation): Flow<String> {
        check(loaded != null) { "no model loaded" }
        // LiteRT-LM Conversation maintains its own KV cache across turns, so
        // we only forward the LATEST user message. If conversation history
        // diverges (e.g., regenerate, edit, branch), call
        // [LiteRtLmEngine.resetConversation] and replay — handled in a future
        // patch when those features land.
        val lastUser = conversation.messages.lastOrNull { it.role == Role.USER }
            ?: error("no user message to respond to")
        return engine.complete(lastUser.content)
    }

    override fun abort() {
        // LiteRT-LM 0.12.0 doesn't expose a public cancel hook. Cancelling the
        // collecting coroutine is sufficient — the Flow stops emitting and
        // the SDK's internal generation winds down at the next token boundary.
    }
}
