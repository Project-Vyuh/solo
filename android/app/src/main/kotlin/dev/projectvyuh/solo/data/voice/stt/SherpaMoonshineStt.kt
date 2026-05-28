package dev.projectvyuh.solo.data.voice.stt

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "SoloVoiceStt"

/**
 * [Stt] implementation backed by sherpa-onnx Moonshine recognizer.
 *
 * sherpa-onnx 1.13.x exposes Moonshine through [OfflineRecognizer] — the
 * "offline" naming refers to non-streaming-within-utterance decoding, not
 * "requires internet." We feed a full speech-detected utterance (delimited
 * by Silero VAD) and receive the transcript.
 *
 * For Phase 1B initial scope this offline-per-utterance pattern is the right
 * choice: it's simpler than streaming token emission, fits Moonshine's
 * design intent (process exactly the audio of one utterance, no padding),
 * and Moonshine's Ergodic Streaming Encoder is already what makes the
 * "offline" path so fast (107 ms TTFT for typical 3-5 s utterances).
 *
 * True token-by-token streaming during the utterance is deferred to a
 * future PR — it requires `OnlineRecognizer` plumbing which is currently
 * Zipformer-only in sherpa-onnx.
 *
 * @param encoderPath  absolute path to `encoder.onnx`
 * @param decoderPath  absolute path to `decoder.onnx`
 * @param tokensPath   absolute path to `tokens.txt`
 * @param uncachedDecoderPath optional uncached-decoder ONNX (Moonshine v2 ships both)
 */
class SherpaMoonshineStt(
    private val encoderPath: String,
    private val decoderPath: String,
    private val tokensPath: String,
    private val uncachedDecoderPath: String? = null,
) : Stt {

    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    suspend fun initialize() = mutex.withLock {
        if (recognizer != null) return@withLock
        val moonshine = OfflineMoonshineModelConfig(
            preprocessor = encoderPath,
            encoder = encoderPath,
            uncachedDecoder = uncachedDecoderPath ?: decoderPath,
            cachedDecoder = decoderPath,
        )
        val model = OfflineModelConfig(
            moonshine = moonshine,
            tokens = tokensPath,
            numThreads = 4,
            provider = "cpu",
            modelType = "moonshine",
        )
        val feat = FeatureConfig(sampleRate = 16_000, featureDim = 80)
        val config = OfflineRecognizerConfig(featConfig = feat, modelConfig = model)
        recognizer = OfflineRecognizer(config = config)
        Log.i(TAG, "Moonshine STT initialized")
    }

    override suspend fun openSession(): SttSession {
        check(recognizer != null) { "STT not initialized" }
        return MoonshineSession(recognizer!!)
    }

    suspend fun release() = mutex.withLock {
        runCatching { recognizer?.release() }
        recognizer = null
    }
}

/**
 * One utterance worth of session. Frames are appended via [acceptFrame];
 * when the caller [close]s the session, decoding runs and the [transcripts]
 * Flow emits the single final transcript.
 *
 * Token-by-token partial emission can be added later by interleaving
 * `recognizer.decode` calls between chunks; not implemented in this initial
 * cut to keep the API surface minimal.
 */
private class MoonshineSession(
    private val recognizer: OfflineRecognizer,
) : SttSession {

    private val frames = mutableListOf<ShortArray>()
    private val transcriptChannel = Channel<String>(capacity = Channel.UNLIMITED)

    override suspend fun acceptFrame(samples: ShortArray) {
        frames.add(samples)
    }

    override fun transcripts(): Flow<String> = transcriptChannel.consumeAsFlow()

    override suspend fun close() {
        withContext(Dispatchers.Default) {
            // Concatenate captured frames into a single normalized FloatArray
            val totalSamples = frames.sumOf { it.size }
            val pcm = FloatArray(totalSamples)
            var i = 0
            for (frame in frames) {
                for (j in frame.indices) {
                    pcm[i++] = frame[j] / 32768f
                }
            }
            // Run Moonshine on the full utterance
            val stream = recognizer.createStream()
            stream.acceptWaveform(samples = pcm, sampleRate = 16_000)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            stream.release()
            transcriptChannel.trySend(result.text)
            transcriptChannel.close()
        }
    }
}
