package dev.projectvyuh.solo.domain.model

/**
 * Who produced a [Message].
 *
 * - SYSTEM: top-level instructions establishing identity, behavior, safety rules.
 *           In templates that don't have a separate system role (Gemma), the
 *           system content is prepended to the first user turn.
 * - USER:   the human asking / instructing Solo.
 * - ASSISTANT: Solo's reply.
 * - TOOL:   structured output of a tool invocation, fed back into the model.
 *           Reserved for the agent phase; not used in Phase 1A chat.
 */
enum class Role {
    SYSTEM, USER, ASSISTANT, TOOL
}
