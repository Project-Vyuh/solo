package dev.projectvyuh.solo.data.llm

import dev.projectvyuh.solo.data.llm.jni.LlamaBridge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level Kotlin wrapper around [LlamaBridge].
 *
 * Threading invariant: llama.cpp's `llama_context` is NOT thread-safe. All
 * native calls go through [inferenceDispatcher], a single-threaded pool that
 * serializes inference. The [mutex] additionally prevents concurrent
 * load/unload/generate calls from racing through the dispatcher.
 *
 * Lifecycle: a process holds at most one loaded model. Switching models
 * requires [unload] then [load].
 */
@Singleton
class LlamaCppEngine @Inject constructor() {

    private val bridge = LlamaBridge()
    private val mutex = Mutex()
    private val inferenceDispatcher: CoroutineDispatcher =
        @Suppress("OPT_IN_USAGE")
        Dispatchers.IO.limitedParallelism(1)

    @Volatile private var handle: Long = 0L
    @Volatile private var loadedMetadata: ModelMetadata? = null

    init {
        bridge.nativeInitBackend()
    }

    /** True if a model is currently resident in memory. */
    val isLoaded: Boolean get() = handle != 0L

    /** Metadata for the currently loaded model, or null. */
    val metadata: ModelMetadata? get() = loadedMetadata

    fun nativeVersion(): String = bridge.nativeVersion()

    /**
     * Load a GGUF model from [path]. Idempotent if called with the same params
     * while loaded; if a different model is loaded, throws.
     */
    suspend fun load(
        path: String,
        contextSize: Int = 4096,
        threadCount: Int = 4,
        gpuLayers: Int = 0,
    ): ModelMetadata = mutex.withLock {
        if (handle != 0L) {
            return@withLock loadedMetadata
                ?: error("engine reports loaded but metadata is null")
        }
        withContext(inferenceDispatcher) {
            val h = bridge.nativeLoadModel(path, contextSize, threadCount, gpuLayers)
            if (h == 0L) error("failed to load model: $path")
            handle = h
            val meta = ModelMetadata.fromJson(bridge.nativeGetModelInfo(h))
            loadedMetadata = meta
            meta
        }
    }

    /** Release the loaded model. No-op if nothing is loaded. */
    suspend fun unload() = mutex.withLock {
        val h = handle
        if (h == 0L) return@withLock
        withContext(inferenceDispatcher) {
            bridge.nativeUnloadModel(h)
        }
        handle = 0L
        loadedMetadata = null
    }

    /**
     * Stream tokens for [prompt]. Each emitted string is a valid UTF-8 piece
     * (multi-byte codepoints are buffered in native code and never split).
     *
     * Collecting on a cancellable scope and cancelling will also abort the
     * underlying native generation.
     */
    fun complete(prompt: String, params: SamplingParams = SamplingParams()): Flow<String> = callbackFlow {
        val h = handle
        check(h != 0L) { "no model loaded" }

        val callback = object : LlamaBridge.TokenCallback {
            override fun onToken(piece: String): Boolean = trySend(piece).isSuccess
        }

        val inferenceJob = launch(inferenceDispatcher) {
            try {
                bridge.nativeStreamCompletion(
                    handle      = h,
                    prompt      = prompt,
                    maxTokens   = params.maxTokens,
                    temperature = params.temperature,
                    topP        = params.topP,
                    topK        = params.topK,
                    seed        = if (params.seed == -1) System.nanoTime().toInt() else params.seed,
                    callback    = callback,
                )
            } finally {
                close()
            }
        }

        awaitClose {
            // Consumer cancelled or upstream finished. If native is still running,
            // tell it to wind down at its next token check.
            if (inferenceJob.isActive) bridge.nativeAbort(h)
        }
    }.buffer(Channel.UNLIMITED)

    /** Request mid-stream abort of any active generation. */
    fun abort() {
        val h = handle
        if (h != 0L) bridge.nativeAbort(h)
    }
}
