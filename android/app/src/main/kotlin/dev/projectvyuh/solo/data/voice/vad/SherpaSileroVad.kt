package dev.projectvyuh.solo.data.voice.vad

import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SoloVoiceSilero"

/**
 * [SileroVad] implementation backed by sherpa-onnx 1.13.x.
 *
 * sherpa-onnx wraps the official Silero VAD ONNX model with stateful streaming
 * inference — its native side handles the LSTM hidden state between calls,
 * so callers just feed audio and read out probabilities.
 *
 * Threading: sherpa-onnx's Vad is not documented as thread-safe. We guard
 * with a mutex. Audio frames are small and the call is fast (sub-ms), so
 * contention is not a concern.
 *
 * @param modelPath absolute path to `silero_vad.onnx` (downloaded via Solo's
 *   [dev.projectvyuh.solo.core.model.ModelManager])
 * @param threshold P(speech) cutoff for [isSpeech] to report true; default 0.5
 */
class SherpaSileroVad(
    private val modelPath: String,
    private val threshold: Float = 0.5f,
) : SileroVad {

    private val mutex = Mutex()
    private var nativeVad: Vad? = null

    /**
     * Idempotent initialization. Builds the underlying Vad with Silero
     * defaults tuned for our 16 kHz / 30 ms-window pipeline.
     */
    suspend fun initialize() = mutex.withLock {
        if (nativeVad != null) return@withLock
        val silero = SileroVadModelConfig(
            model = modelPath,
            threshold = threshold,
            minSilenceDuration = 0.25f,   // 250 ms silence to consider end-of-speech
            minSpeechDuration = 0.1f,     // 100 ms minimum speech before reporting
            windowSize = 512,             // Silero's native 32 ms frame at 16 kHz
            maxSpeechDuration = 20f,      // hard cap on single utterance length
        )
        val config = VadModelConfig(
            sileroVadModelConfig = silero,
            sampleRate = 16_000,
            numThreads = 1,
            provider = "cpu",
            debug = false,
        )
        nativeVad = Vad(config = config)
        Log.i(TAG, "Silero VAD initialized: threshold=$threshold")
    }

    override fun isSpeech(samples: ShortArray): Float {
        val vad = nativeVad ?: error("VAD not initialized")
        // sherpa-onnx expects normalized float samples in [-1, 1]
        val floats = FloatArray(samples.size) { samples[it] / 32768f }
        vad.acceptWaveform(floats)
        // We return a binary-equivalent confidence — 1.0 if Silero says speech,
        // 0.0 otherwise. For richer per-frame probability we'd need the
        // pre-threshold internal score; sherpa-onnx exposes the binary signal.
        return if (vad.isSpeechDetected()) 1.0f else 0.0f
    }

    override fun reset() {
        runCatching { nativeVad?.reset() }
    }

    suspend fun release() = mutex.withLock {
        runCatching { nativeVad?.release() }
        nativeVad = null
    }
}
