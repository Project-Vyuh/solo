package dev.projectvyuh.solo.data.voice.vad

/**
 * Semantic turn detection — frontier layer that decides "has the user
 * finished saying what they want to say?" based on the partial STT
 * transcript, not just silence duration.
 *
 * This is the layer that distinguishes modern voice agents (GPT-4o Voice,
 * Gemini Live, LiveKit) from older silence-based ones. See SOLO-VOICE.md §3.3
 * for the architectural rationale and Solo's open-source training plan.
 *
 * Solo trains its own Apache-2.0 model since LiveKit's reference
 * implementation is proprietary. Until that model ships, this interface is
 * implemented by [NaiveTurnDetector] (silence-threshold only).
 */
interface SemanticTurnDetector {
    /**
     * @param partialTranscript the latest STT transcript (may be incomplete)
     * @return P(user is finished speaking) ∈ [0.0, 1.0]
     */
    suspend fun predictTurnComplete(partialTranscript: String): Float
}

/**
 * Placeholder implementation that does not actually do semantic detection.
 * Returns 1.0 (always "complete") to defer to the silence-threshold gate.
 * Replaced by the trained model in a later PR.
 */
class NaiveTurnDetector : SemanticTurnDetector {
    override suspend fun predictTurnComplete(partialTranscript: String): Float = 1.0f
}
