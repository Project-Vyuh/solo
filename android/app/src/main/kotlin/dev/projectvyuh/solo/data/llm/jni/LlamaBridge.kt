package dev.projectvyuh.solo.data.llm.jni

/**
 * JNI bridge between Kotlin and the Solo native library (libsolo_native.so),
 * which statically links llama.cpp. Every method here has a matching
 * implementation in solo_jni.cpp.
 *
 * This is the lowest layer — callers should prefer LlamaCppEngine, which
 * wraps these calls in coroutines, Flows, and a safe lifecycle.
 */
class LlamaBridge {

    /** Token streaming callback invoked from the JNI thread during generation. */
    interface TokenCallback {
        /**
         * Called for each UTF-8 piece emitted by the model.
         * @return false to abort generation, true to continue.
         */
        fun onToken(piece: String): Boolean
    }

    external fun nativeVersion(): String

    /** Initialize the llama.cpp backend. Idempotent — safe to call multiple times. */
    external fun nativeInitBackend()

    /** Release backend resources. Call at app shutdown. */
    external fun nativeFreeBackend()

    /**
     * Load a GGUF model from disk via mmap.
     *
     * @param path absolute filesystem path to the .gguf file
     * @param nCtx context window size in tokens (e.g. 4096)
     * @param nThreads inference thread count (typically perf-core count)
     * @param nGpuLayers number of layers to offload to GPU (0 = CPU only)
     * @return opaque session handle, or 0 on failure
     */
    external fun nativeLoadModel(
        path: String,
        nCtx: Int,
        nThreads: Int,
        nGpuLayers: Int,
    ): Long

    /** Release a session loaded with [nativeLoadModel]. */
    external fun nativeUnloadModel(handle: Long)

    /**
     * Stream completion tokens to [callback].
     *
     * @param handle session handle from [nativeLoadModel]
     * @param prompt the full formatted prompt (chat template already applied)
     * @param maxTokens hard cap on tokens to generate
     * @param temperature 0.0 = greedy, >0 = sampled
     * @param topP nucleus sampling threshold (0.0..1.0; 0 disables)
     * @param topK top-k filter (<= 0 disables)
     * @param seed RNG seed for reproducible sampling
     * @return number of tokens actually generated, or -1 on error
     */
    external fun nativeStreamCompletion(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Int,
        callback: TokenCallback,
    ): Int

    /** Request mid-stream abort. The active nativeStreamCompletion exits at the next token. */
    external fun nativeAbort(handle: Long)

    /** Returns a JSON string with model metadata: description, n_ctx, n_vocab, n_params, size_bytes. */
    external fun nativeGetModelInfo(handle: Long): String

    companion object {
        init {
            System.loadLibrary("solo_native")
        }
    }
}
