package dev.projectvyuh.solo.data.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SoloEngine"

/**
 * Solo's on-device inference engine, backed by Google AI Edge LiteRT-LM 0.12.x.
 *
 * Threading model: LiteRT-LM's Engine and Conversation are not documented as
 * thread-safe. We serialize all calls through [mutex] and run them on a
 * dedicated single-thread dispatcher. Backend selection happens once at
 * [load] time and cannot change without a full unload.
 *
 * Lifecycle: at most one Engine + one Conversation per process. The
 * Conversation owns the running KV cache; recreating it resets the
 * conversation, so we keep it alive between turns.
 *
 * API: LiteRT-LM 0.12.x uses [Backend] as a sealed class with factory
 * subclasses (`Backend.GPU()` etc.), [Contents.of] for text content, and
 * `systemInstruction: Contents?` on ConversationConfig.
 */
@Singleton
class LiteRtLmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mutex = Mutex()
    private val inferenceDispatcher: CoroutineDispatcher =
        @Suppress("OPT_IN_USAGE")
        Dispatchers.IO.limitedParallelism(1)

    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var activeBackend: BackendType = BackendType.UNINITIALIZED

    val isLoaded: Boolean get() = engine != null
    val backend: BackendType get() = activeBackend

    /**
     * Load a `.litertlm` model. Idempotent.
     *
     * Tries [preferredBackend] first; on failure falls back to CPU. The first
     * call takes ~5-10 seconds even on flagship hardware as the engine
     * compiles the graph and warms the KV cache.
     */
    suspend fun load(
        modelPath: String,
        preferredBackend: BackendType = BackendType.GPU,
        systemInstruction: String? = null,
    ) = mutex.withLock {
        if (engine != null) {
            Log.i(TAG, "load: already loaded ($activeBackend); skipping")
            return@withLock
        }
        withContext(inferenceDispatcher) {
            Log.i(TAG, "load: attempting backend=$preferredBackend, path=$modelPath")
            val (createdEngine, backendUsed) = try {
                buildEngine(modelPath, preferredBackend) to preferredBackend
            } catch (t: Throwable) {
                Log.w(TAG, "load: $preferredBackend failed, falling back to CPU: ${t.message}")
                if (preferredBackend == BackendType.CPU) throw t
                buildEngine(modelPath, BackendType.CPU) to BackendType.CPU
            }
            engine = createdEngine
            activeBackend = backendUsed
            conversation = createdEngine.createConversation(
                ConversationConfig(
                    systemInstruction = systemInstruction?.let { Contents.of(it) },
                    samplerConfig = SamplerConfig(
                        topK        = 40,
                        topP        = 0.95,
                        temperature = 0.7,
                    ),
                )
            )
            Log.i(TAG, "load: done; backend=$activeBackend")
        }
    }

    private fun buildEngine(modelPath: String, backendType: BackendType): Engine {
        val backend: Backend = when (backendType) {
            BackendType.GPU -> Backend.GPU()
            BackendType.NPU -> Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
            BackendType.CPU,
            BackendType.UNINITIALIZED -> Backend.CPU()
        }
        val config = EngineConfig(
            modelPath = modelPath,
            backend   = backend,
            cacheDir  = context.cacheDir.path,
        )
        val newEngine = Engine(config)
        newEngine.initialize()
        return newEngine
    }

    /**
     * Stream the assistant's reply to [userMessage] as UTF-8 pieces.
     *
     * The system instruction is set on the Conversation at load time. Multi-turn
     * context is automatically maintained by the underlying Conversation's KV
     * cache.
     */
    fun complete(userMessage: String): Flow<String> = flow {
        val conv = conversation ?: error("engine not loaded")
        Log.i(TAG, "complete: sending message, len=${userMessage.length}")
        conv.sendMessageAsync(Contents.of(userMessage)).collect { msg ->
            emit(msg.toString())
        }
        Log.i(TAG, "complete: stream ended")
    }.flowOn(inferenceDispatcher)

    /** Reset the conversation, clearing KV cache. Engine stays loaded. */
    suspend fun resetConversation(systemInstruction: String? = null) = mutex.withLock {
        val eng = engine ?: return@withLock
        withContext(inferenceDispatcher) {
            conversation?.close()
            conversation = eng.createConversation(
                ConversationConfig(
                    systemInstruction = systemInstruction?.let { Contents.of(it) },
                )
            )
        }
    }

    suspend fun unload() = mutex.withLock {
        withContext(inferenceDispatcher) {
            conversation?.close()
            engine?.close()
            conversation = null
            engine = null
            activeBackend = BackendType.UNINITIALIZED
        }
    }

    enum class BackendType { UNINITIALIZED, CPU, GPU, NPU }
}
