package dev.projectvyuh.solo.data.voice.orchestration

/**
 * Tunable parameters for one [VoiceOrchestrator] session.
 *
 * Defaults are chosen for the target Phase 1B device class (Snapdragon
 * 6 Gen 3, 8 GB RAM) and the latency budget from SOLO-VOICE.md §5.
 *
 * @param hardSilenceTimeoutMs upper bound on silence before forcing a turn
 *   commit even when the semantic detector is unsure. Belt-and-suspenders
 *   for when the user trails off without finishing.
 * @param semanticTurnThreshold P(turn-complete) at which the semantic
 *   detector commits the turn. The naive detector currently always returns
 *   1.0 so this effectively defers to [hardSilenceTimeoutMs]; the trained
 *   model will use 0.7 once shipped.
 * @param minUtteranceMs minimum speech length before we accept a turn. Avoids
 *   committing on stray micro-noise (a single "uh" tick).
 * @param ttsVoiceId Kokoro voice preset id. 0 = af_bella (warm female),
 *   11 = am_michael (warm male). See SOLO-VOICE.md §3.6.
 */
data class VoiceSessionConfig(
    val hardSilenceTimeoutMs: Long = 1_500,
    val semanticTurnThreshold: Float = 0.7f,
    val minUtteranceMs: Long = 300,
    val ttsVoiceId: Int = 0,
    val sttFrameMs: Int = 20,
)
