package dev.projectvyuh.solo.data.voice.orchestration

/**
 * The state machine for a single wake-to-reply voice turn.
 *
 * State transitions (Phase 1B initial — tap-to-speak):
 *   Idle
 *     ↓ user taps mic
 *   Listening (mic open; Silero VAD + Moonshine STT running)
 *     ↓ semantic turn detector + silence threshold fires
 *   Thinking (Gemma 4 prefilling + generating)
 *     ↓ first sentence complete
 *   Speaking (Kokoro TTS streaming audio chunks)
 *     ↓ LLM completes + all chunks played
 *   Idle
 *
 * Future state additions (Phase 1B.2):
 *   Idle -> Listening via WakeEvent (always-on)
 *   Speaking -> Interrupted (barge-in) -> Listening
 */
sealed interface VoiceTurnState {
    object Idle           : VoiceTurnState
    data class Listening(val partialTranscript: String = "") : VoiceTurnState
    data class Thinking(val userTranscript: String)          : VoiceTurnState
    data class Speaking(val partialResponse: String)         : VoiceTurnState
    data class Failed(val cause: Throwable)                  : VoiceTurnState
}
