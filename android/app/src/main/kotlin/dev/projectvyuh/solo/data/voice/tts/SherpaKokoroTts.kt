package dev.projectvyuh.solo.data.voice.tts

import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SoloVoiceTts"

/**
 * [TtsEngine] implementation backed by sherpa-onnx Kokoro TTS.
 *
 * Phase 1B primary TTS engine. See SOLO-VOICE.md §3.6 for the choice rationale.
 *
 * Threading: sherpa-onnx's OfflineTts.generate is a blocking native call. We
 * run on Dispatchers.Default, serialize with a mutex (the engine is stateful
 * during a generate call), and chunk the output samples for streaming.
 *
 * @param modelDir       absolute path to the directory containing Kokoro's
 *                       `model.onnx`, `voices.bin`, `tokens.txt`, etc.
 * @param dataDir        path to the `espeak-ng-data` directory (Kokoro uses
 *                       eSpeak-NG for phonemization)
 * @param defaultVoiceId integer voice index per Kokoro's voices.bin layout.
 *                       Initial pick: 0 (`af_bella`, warm female)
 *                       Alternative: 11 (`am_michael`, warm male)
 */
class SherpaKokoroTts(
    private val modelDir: String,
    private val dataDir: String,
    private val defaultVoiceId: Int = 0,
) : TtsEngine {

    private val mutex = Mutex()
    private var tts: OfflineTts? = null
    private var activeVoiceId: Int = defaultVoiceId

    override val outputSampleRateHz: Int = 24_000   // Kokoro's native rate

    override suspend fun load(modelPath: String, voicePresetId: String) = mutex.withLock {
        if (tts != null) return@withLock
        // voicePresetId is parseable as an int for Kokoro (which uses indexed voices).
        // For richer named-preset support we'll add a lookup table in a follow-up.
        activeVoiceId = voicePresetId.toIntOrNull() ?: defaultVoiceId

        val kokoro = OfflineTtsKokoroModelConfig(
            model = "$modelDir/model.onnx",
            voices = "$modelDir/voices.bin",
            tokens = "$modelDir/tokens.txt",
            dataDir = dataDir,
        )
        val model = OfflineTtsModelConfig(
            kokoro = kokoro,
            numThreads = 2,
            provider = "cpu",
            debug = false,
        )
        val config = OfflineTtsConfig(model = model)
        tts = OfflineTts(config = config)
        Log.i(TAG, "Kokoro TTS loaded; voice=$activeVoiceId")
    }

    /**
     * Synthesize [text] into PCM16 samples.
     *
     * Phase 1B initial: emits the full utterance as a single chunk (one
     * sentence per call, fed by [SentenceChunker] upstream — so chunks ARE
     * effectively sentence-sized streaming). Streaming-within-sentence
     * (Kokoro's `generateWithCallback`) is a Phase 1B.2 enhancement.
     */
    override fun synthesize(text: String): Flow<ShortArray> = flow {
        val engine = tts ?: error("TTS not loaded")
        val audio = mutex.withLock {
            engine.generate(text = text, sid = activeVoiceId, speed = 1.0f)
        }
        // Convert sherpa-onnx's FloatArray [-1, 1] output to ShortArray PCM16
        val floatSamples = audio.samples
        val pcm = ShortArray(floatSamples.size) { i ->
            val clamped = floatSamples[i].coerceIn(-1.0f, 1.0f)
            (clamped * 32767f).toInt().toShort()
        }
        emit(pcm)
    }.flowOn(Dispatchers.Default)

    override suspend fun unload() = mutex.withLock {
        runCatching { tts?.release() }
        tts = null
    }
}
