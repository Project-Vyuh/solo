package dev.projectvyuh.solo.data.voice.stt

import kotlinx.coroutines.flow.Flow

/**
 * Streaming speech-to-text.
 *
 * Phase 1B target: Moonshine v2 medium-en (245M params, 107ms TTFT, 6.65% WER).
 * See SOLO-VOICE.md §3.4 for the rationale.
 *
 * Stateful: open a session, push audio frames, collect the streaming
 * transcript Flow. Close when the turn ends to flush and reset the model's
 * cache.
 */
interface Stt {
    /**
     * Open a new streaming session. The returned [SttSession] accepts audio
     * frames and emits partial transcript updates.
     */
    suspend fun openSession(): SttSession
}

interface SttSession {
    /** Push a frame of PCM16 mono 16 kHz audio. */
    suspend fun acceptFrame(samples: ShortArray)

    /**
     * Partial-and-final transcript stream. Emits whenever the model
     * produces new tokens. Final value before completion is the full
     * utterance.
     */
    fun transcripts(): Flow<String>

    /** Flush the model's internal cache and close the session. */
    suspend fun close()
}
