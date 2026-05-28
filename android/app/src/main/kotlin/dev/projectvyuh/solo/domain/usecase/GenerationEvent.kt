package dev.projectvyuh.solo.domain.usecase

import dev.projectvyuh.solo.domain.model.Conversation

/**
 * Lifecycle event emitted by [SendMessageUseCase] as the model produces a reply.
 *
 * The UI consumes this stream rather than raw tokens: each [TokenEmitted] event
 * carries the *complete* conversation snapshot including the partial assistant
 * message so far. That keeps state management in the ViewModel trivial — just
 * forward conversation to UI on each event.
 */
sealed interface GenerationEvent {
    /** A new token (or multi-token piece) was appended. */
    data class TokenEmitted(val conversation: Conversation) : GenerationEvent

    /** Generation finished cleanly. Conversation contains the final assistant message. */
    data class Done(val conversation: Conversation) : GenerationEvent

    /** Generation failed mid-stream. The conversation may be partial. */
    data class Failed(val conversation: Conversation, val cause: Throwable) : GenerationEvent
}
