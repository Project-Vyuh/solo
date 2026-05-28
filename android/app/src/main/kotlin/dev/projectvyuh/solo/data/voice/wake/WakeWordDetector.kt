package dev.projectvyuh.solo.data.voice.wake

import kotlinx.coroutines.flow.Flow

/**
 * Always-on wake word detection ("Hey Solo").
 *
 * Phase 1B initial scope: tap-to-speak only. Wake word is Phase 1B.2 once
 * the foundational pipeline is verified.
 *
 * Backed by openWakeWord (Apache 2.0) via sherpa-onnx. The "Hey Solo" model
 * is Solo-trained per SOLO-VOICE.md §3.1 and shipped with the app.
 *
 * Targets sub-100ms detection latency at <0.1 false-accept/hour.
 */
interface WakeWordDetector {
    /**
     * Stream wake events. Emits a [WakeEvent] each time the configured
     * wake phrase is detected with confidence above threshold.
     */
    fun events(): Flow<WakeEvent>
}

data class WakeEvent(
    val phraseId: String,                 // currently always "hey_solo"
    val confidence: Float,                // model probability ∈ [0,1]
    val timestampMs: Long = System.currentTimeMillis(),
)
