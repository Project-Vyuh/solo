package dev.projectvyuh.solo.data.voice.vad

/**
 * Audio-level voice activity detection (Silero VAD).
 *
 * Stateful: feeds frames one at a time and tracks rolling state. Caller is
 * expected to call [reset] between distinct utterances.
 *
 * Phase 1B initial implementation is a STUB. Real implementation arrives in
 * the sherpa-onnx integration PR — sherpa-onnx ships Silero VAD natively.
 */
interface SileroVad {
    /**
     * @param samples a frame of PCM16 mono 16 kHz audio
     * @return P(speech) ∈ [0.0, 1.0] for this frame
     */
    fun isSpeech(samples: ShortArray): Float

    /** Reset internal LSTM state between utterances. */
    fun reset()
}
