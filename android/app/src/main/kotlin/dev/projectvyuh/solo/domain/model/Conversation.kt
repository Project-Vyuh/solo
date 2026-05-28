package dev.projectvyuh.solo.domain.model

import java.util.UUID

/**
 * A complete chat session: an ordered sequence of [Message]s.
 *
 * The first message is conventionally a [Role.SYSTEM] message defining Solo's
 * identity and behavior. Subsequent messages alternate USER and ASSISTANT.
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val messages: List<Message> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
) {
    fun append(message: Message): Conversation = copy(messages = messages + message)

    /** Replace the last message (used while an assistant reply is streaming). */
    fun replaceLast(message: Message): Conversation {
        require(messages.isNotEmpty()) { "cannot replace last on empty conversation" }
        return copy(messages = messages.dropLast(1) + message)
    }

    val lastMessage: Message? get() = messages.lastOrNull()
}
