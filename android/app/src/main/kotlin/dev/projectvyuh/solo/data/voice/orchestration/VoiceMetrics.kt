package dev.projectvyuh.solo.data.voice.orchestration

import android.util.Log

private const val TAG = "SoloVoiceMetrics"

/**
 * Per-turn voice pipeline timing instrumentation.
 *
 * Captures the latency budget from SOLO-VOICE.md §5 in real measurements so
 * we can verify we hit the targets on the target device class and surface
 * regressions early.
 *
 * Usage:
 *   val metrics = VoiceMetrics().apply { markWake() }
 *   // ... pipeline stages
 *   metrics.markSttFirstToken()
 *   metrics.markLlmFirstToken()
 *   metrics.markTtsFirstAudio()
 *   metrics.markComplete()
 *   metrics.log()
 *
 * Logged values feed the Activity tab (Phase 1B.2) and surface in benchmark
 * runs (validation criteria §10).
 */
class VoiceMetrics {
    private var wakeAtNs: Long = 0
    private var sttFirstTokenAtNs: Long = 0
    private var sttCompleteAtNs: Long = 0
    private var turnCompleteAtNs: Long = 0
    private var llmFirstTokenAtNs: Long = 0
    private var ttsFirstAudioAtNs: Long = 0
    private var doneAtNs: Long = 0

    fun markWake()             { wakeAtNs = System.nanoTime() }
    fun markSttFirstToken()    { if (sttFirstTokenAtNs == 0L) sttFirstTokenAtNs = System.nanoTime() }
    fun markSttComplete()      { sttCompleteAtNs = System.nanoTime() }
    fun markTurnComplete()     { turnCompleteAtNs = System.nanoTime() }
    fun markLlmFirstToken()    { if (llmFirstTokenAtNs == 0L) llmFirstTokenAtNs = System.nanoTime() }
    fun markTtsFirstAudio()    { if (ttsFirstAudioAtNs == 0L) ttsFirstAudioAtNs = System.nanoTime() }
    fun markComplete()         { doneAtNs = System.nanoTime() }

    private fun ms(fromNs: Long, toNs: Long): Long =
        if (fromNs == 0L || toNs == 0L) -1L else (toNs - fromNs) / 1_000_000

    fun log() {
        if (wakeAtNs == 0L) return
        Log.i(TAG, buildString {
            append("voice turn timings (ms): ")
            append("wake→sttToken=").append(ms(wakeAtNs, sttFirstTokenAtNs))
            append(" wake→sttDone=").append(ms(wakeAtNs, sttCompleteAtNs))
            append(" wake→turnDone=").append(ms(wakeAtNs, turnCompleteAtNs))
            append(" wake→llmToken=").append(ms(wakeAtNs, llmFirstTokenAtNs))
            append(" wake→ttsAudio=").append(ms(wakeAtNs, ttsFirstAudioAtNs))
            append(" wake→complete=").append(ms(wakeAtNs, doneAtNs))
        })
    }
}
