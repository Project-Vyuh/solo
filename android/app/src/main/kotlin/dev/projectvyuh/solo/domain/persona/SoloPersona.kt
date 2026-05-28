package dev.projectvyuh.solo.domain.persona

/**
 * Stable identity facts about Solo that the system prompt and UI both refer
 * to. Centralized so we change them in one place.
 *
 * NOT user-mutable — these are product identity, not preferences.
 */
data class SoloPersona(
    val name: String,
    val appVersion: String,
    val phase: Phase,
) {
    enum class Phase(val label: String, val capabilities: List<String>, val notYet: List<String>) {
        // Phase 1A: chat + on-device LLM + privacy enforcement. No tools, no
        // proactive action, no voice, no memory across sessions, no agent layer.
        PHASE_1A(
            label = "Phase 1A — Foundation",
            capabilities = listOf(
                "Run an on-device LLM (Gemma 3n E4B) fully locally with no data leaving the phone",
                "Hold a text conversation",
                "Reason about general topics using your training",
                "Show your reasoning when asked or when the question is multi-step",
            ),
            notYet = listOf(
                "Voice input or output",
                "Reading or acting on apps, messages, calendars, or health data",
                "Remembering anything between app launches",
                "Acting autonomously without being asked",
                "Web search or any other live information access",
            ),
        ),
    }

    companion object {
        val Current = SoloPersona(
            name = "Solo",
            appVersion = "0.0.1",
            phase = Phase.PHASE_1A,
        )
    }
}
