package dev.projectvyuh.solo.data.voice.orchestration

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.projectvyuh.solo.core.model.ModelManager
import dev.projectvyuh.solo.core.model.VoiceModelRegistry
import dev.projectvyuh.solo.data.voice.audio.AudioCapture
import dev.projectvyuh.solo.data.voice.audio.AudioPlayback
import dev.projectvyuh.solo.data.voice.stt.SherpaMoonshineStt
import dev.projectvyuh.solo.data.voice.tts.SentenceChunker
import dev.projectvyuh.solo.data.voice.tts.SherpaKokoroTts
import dev.projectvyuh.solo.data.voice.vad.NaiveTurnDetector
import dev.projectvyuh.solo.data.voice.vad.SemanticTurnDetector
import dev.projectvyuh.solo.data.voice.vad.SherpaSileroVad
import dev.projectvyuh.solo.domain.model.Conversation
import dev.projectvyuh.solo.domain.model.Message
import dev.projectvyuh.solo.domain.model.Role
import dev.projectvyuh.solo.domain.persona.SoloSystemPrompt
import dev.projectvyuh.solo.domain.repository.LlmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SoloVoiceOrchestrator"

/**
 * Coordinates Solo's voice pipeline end-to-end for one wake→reply turn.
 *
 * The state machine (see [VoiceTurnState]):
 *
 *   Idle
 *     ↓ [startTurn]
 *   Listening (mic open; Silero VAD + Moonshine STT accumulating)
 *     ↓ silence threshold OR semantic turn-complete fires
 *   Thinking (Gemma 4 prefilling + generating)
 *     ↓ first sentence completes from LLM stream
 *   Speaking (Kokoro synthesizing + AudioTrack playing)
 *     ↓ LLM completes AND last PCM chunk played
 *   Idle
 *
 * Streaming overlap (this is the frontier-pattern win from SOLO-VOICE.md §2.2):
 *   - LLM token stream feeds [SentenceChunker]
 *   - Each completed sentence is immediately synthesized by TTS while the LLM
 *     is still generating the next sentence
 *   - First PCM chunk plays while subsequent sentences are still being
 *     synthesized → end-to-end latency ≈ max(stage) not sum(stage)
 *
 * Engine lifecycle: sherpa-onnx VAD/STT/TTS are lazy-initialized on the first
 * [startTurn] call. Subsequent turns reuse the loaded engines.
 *
 * Threading: a single orchestrator [scope] owns all coroutines. [startTurn]
 * is mutex-protected to refuse concurrent turns. [abort] cancels the active
 * turn and returns the state machine to Idle.
 */
@Singleton
class VoiceOrchestrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val llm: LlmRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<VoiceTurnState>(VoiceTurnState.Idle)
    val state: StateFlow<VoiceTurnState> = _state.asStateFlow()

    private val audioCapture = AudioCapture(frameSizeMs = 20)
    private val audioPlayback = AudioPlayback(sampleRateHz = 24_000)

    // sherpa-onnx engines — lazy-initialized
    @Volatile private var vad: SherpaSileroVad? = null
    @Volatile private var stt: SherpaMoonshineStt? = null
    @Volatile private var tts: SherpaKokoroTts? = null
    private val turnDetector: SemanticTurnDetector = NaiveTurnDetector()

    @Volatile private var activeTurn: Job? = null

    /**
     * True if all three voice models are downloaded. Caller should check this
     * before calling [startTurn]; if false, route the user to onboarding to
     * download the voice pack.
     */
    val areVoiceModelsInstalled: Boolean
        get() = VoiceModelRegistry.all.all { modelManager.isInstalled(it) }

    /**
     * Begin a voice turn. Returns immediately; observe [state] to follow
     * progress. No-op if already in flight; call [abort] first to restart.
     */
    suspend fun startTurn(config: VoiceSessionConfig = VoiceSessionConfig()) {
        mutex.withLock {
            if (activeTurn?.isActive == true) {
                Log.i(TAG, "startTurn: already active; ignoring")
                return
            }
            check(areVoiceModelsInstalled) { "voice models not installed" }
            ensureEnginesLoaded()
            activeTurn = scope.launch { runTurn(config) }
        }
    }

    /** Cancel the in-flight turn and return to Idle. */
    suspend fun abort() {
        val j = activeTurn
        activeTurn = null
        j?.cancel()
        audioPlayback.stop()
        _state.value = VoiceTurnState.Idle
    }

    suspend fun shutdown() = mutex.withLock {
        activeTurn?.cancel()
        audioPlayback.release()
        vad?.release()
        stt?.release()
        tts?.unload()
        vad = null; stt = null; tts = null
        scope.cancel()
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private suspend fun ensureEnginesLoaded() = withContext(Dispatchers.IO) {
        if (vad == null) {
            val def = VoiceModelRegistry.SILERO_VAD
            val v = SherpaSileroVad(modelPath = modelManager.modelFile(def).absolutePath)
            v.initialize()
            vad = v
        }
        if (stt == null) {
            val def = VoiceModelRegistry.MOONSHINE_BASE_EN_INT8
            val dir = modelManager.extractDir(def)
            val s = SherpaMoonshineStt(
                encoderPath = "$dir/encoder.int8.onnx",
                decoderPath = "$dir/cached_decoder.int8.onnx",
                tokensPath  = "$dir/tokens.txt",
                uncachedDecoderPath = "$dir/uncached_decoder.int8.onnx",
            )
            s.initialize()
            stt = s
        }
        if (tts == null) {
            val def = VoiceModelRegistry.KOKORO_INT8_MULTILANG_V1_1
            val dir = modelManager.extractDir(def).absolutePath
            val t = SherpaKokoroTts(
                modelDir = dir,
                dataDir  = "$dir/espeak-ng-data",
                defaultVoiceId = 0,
            )
            t.load(modelPath = dir, voicePresetId = "0")
            tts = t
        }
    }

    private suspend fun runTurn(config: VoiceSessionConfig) {
        val metrics = VoiceMetrics().apply { markWake() }
        try {
            // ---------- LISTEN ----------
            _state.value = VoiceTurnState.Listening()
            val transcript = listenAndTranscribe(config, metrics)
            if (transcript.isBlank()) {
                Log.i(TAG, "turn ended: empty transcript")
                _state.value = VoiceTurnState.Idle
                return
            }
            metrics.markTurnComplete()
            Log.i(TAG, "transcript: $transcript")

            // ---------- THINK ----------
            _state.value = VoiceTurnState.Thinking(userTranscript = transcript)
            val tokens = runLlm(transcript, metrics)

            // ---------- SPEAK (streaming overlap) ----------
            _state.value = VoiceTurnState.Speaking(partialResponse = "")
            speakOverlapped(tokens, metrics)

            metrics.markComplete()
            metrics.log()
            _state.value = VoiceTurnState.Idle
        } catch (t: Throwable) {
            Log.e(TAG, "turn failed", t)
            _state.value = VoiceTurnState.Failed(t)
        }
    }

    private suspend fun listenAndTranscribe(
        config: VoiceSessionConfig,
        metrics: VoiceMetrics,
    ): String {
        val v = vad ?: error("vad not loaded")
        val s = stt ?: error("stt not loaded")
        v.reset()
        val session = s.openSession()
        try {
            var firstSpeechAtNs = 0L
            var lastSpeechAtNs = 0L
            var sawSpeech = false

            audioCapture.frames(scope).collect { frame ->
                session.acceptFrame(frame)
                val isSpeech = v.isSpeech(frame) > 0.5f
                val now = System.nanoTime()
                if (isSpeech) {
                    if (firstSpeechAtNs == 0L) {
                        firstSpeechAtNs = now
                        metrics.markSttFirstToken()
                    }
                    lastSpeechAtNs = now
                    sawSpeech = true
                }
                if (sawSpeech && !isSpeech) {
                    val silenceMs = (now - lastSpeechAtNs) / 1_000_000
                    val utteranceMs = (lastSpeechAtNs - firstSpeechAtNs) / 1_000_000
                    if (utteranceMs >= config.minUtteranceMs && silenceMs >= config.hardSilenceTimeoutMs) {
                        // Hard-silence boundary committed the turn.
                        return@collect
                    }
                }
            }
            metrics.markSttComplete()
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Caller aborted — fall through to close the session and return what we have.
        }
        // Close the session: this triggers Moonshine decoding on the accumulated audio.
        val transcripts = session.transcripts()
        session.close()
        var text = ""
        transcripts.collect { text = it }
        return text.trim()
    }

    private suspend fun runLlm(prompt: String, metrics: VoiceMetrics): kotlinx.coroutines.flow.Flow<String> {
        // Wrap the user transcript in a single-turn conversation. The existing
        // LlmRepository uses the last USER message as the prompt. Voice replies
        // are shorter and prose-only — the SoloSystemPrompt was set when the
        // engine was loaded; we add a voice-specific instruction inline.
        val voicePrefix = "(The user is speaking. Reply in 1-3 conversational sentences, " +
            "prose only — no markdown, no lists, no code.) "
        val conversation = Conversation(
            messages = listOf(
                Message(role = Role.SYSTEM, content = SoloSystemPrompt.build()),
                Message(role = Role.USER, content = voicePrefix + prompt),
            )
        )
        val stream = llm.generate(conversation)
        return kotlinx.coroutines.flow.flow {
            stream.collect { piece ->
                metrics.markLlmFirstToken()
                emit(piece)
            }
        }
    }

    private suspend fun speakOverlapped(
        tokens: kotlinx.coroutines.flow.Flow<String>,
        metrics: VoiceMetrics,
    ) {
        val t = tts ?: error("tts not loaded")
        val chunker = SentenceChunker()
        audioPlayback.start()
        var spokenSoFar = StringBuilder()

        chunker.chunk(tokens).collect { sentence ->
            spokenSoFar.append(sentence).append(' ')
            _state.value = VoiceTurnState.Speaking(partialResponse = spokenSoFar.toString())

            // Synthesize + play this sentence. Subsequent sentences will overlap
            // with the LLM still generating their later content.
            t.synthesize(sentence).collect { pcm ->
                metrics.markTtsFirstAudio()
                audioPlayback.write(pcm)
            }
        }
        audioPlayback.stop()
    }
}
