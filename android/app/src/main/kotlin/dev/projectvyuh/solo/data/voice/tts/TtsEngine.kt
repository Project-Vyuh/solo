package dev.projectvyuh.solo.data.voice.tts

import kotlinx.coroutines.flow.Flow

/**
 * Text-to-speech engine abstraction.
 *
 * Phase 1B target: Kokoro-82M v1.0 via sherpa-onnx (~300ms TTFA on mobile).
 * Future swaps:
 *   - Phase 1B.2: NeuTTS Air 748M (voice cloning, instant)
 *   - Phase 1C: Qwen3-TTS 0.6B (97ms latency, 10 languages, voice clone)
 *
 * See SOLO-VOICE.md §3.6 and §3.7 for the decision rationale and upgrade
 * pipeline. The interface is intentionally minimal so swapping engines is a
 * one-file change.
 */
interface TtsEngine {
    /** Load model + voice preset. Idempotent if already loaded with same args. */
    suspend fun load(modelPath: String, voicePresetId: String)

    /**
     * Synthesize [text] and stream PCM16 audio chunks. Each emitted ShortArray
     * is a chunk of mono PCM16 at [outputSampleRateHz].
     *
     * Streaming: caller can emit successive sentences via repeated calls and
     * the underlying engine may apply caching where possible (Kokoro v1.0
     * is mostly stateless; future engines like Qwen3-TTS share state across
     * sentences for prosody continuity).
     */
    fun synthesize(text: String): Flow<ShortArray>

    /** Sample rate of audio produced by [synthesize]. */
    val outputSampleRateHz: Int

    suspend fun unload()
}
