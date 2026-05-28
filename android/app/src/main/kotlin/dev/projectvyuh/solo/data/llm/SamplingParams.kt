package dev.projectvyuh.solo.data.llm

/**
 * Sampling parameters for a single generation call.
 *
 * Defaults are tuned for conversational chat. For deterministic outputs
 * (e.g. tool-call generation), use [greedy].
 */
data class SamplingParams(
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val seed: Int = -1,
) {
    companion object {
        /** Deterministic: temperature=0 selects the argmax token every step. */
        val greedy = SamplingParams(temperature = 0f, topP = 0f, topK = 0)
    }
}
