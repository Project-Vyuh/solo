package dev.projectvyuh.solo.domain.model

import java.util.UUID

/**
 * A single turn in a conversation.
 *
 * Immutable by design: when the assistant is mid-stream, the UI builds a new
 * Message instance per token rather than mutating an existing one. This keeps
 * the conversation snapshot consistent for any consumer reading it
 * concurrently with generation.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
    /** True while this assistant message is still being streamed from the model. */
    val isStreaming: Boolean = false,
)
