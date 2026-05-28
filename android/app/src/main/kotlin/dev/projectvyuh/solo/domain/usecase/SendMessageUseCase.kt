package dev.projectvyuh.solo.domain.usecase

import dev.projectvyuh.solo.domain.model.Conversation
import dev.projectvyuh.solo.domain.model.Message
import dev.projectvyuh.solo.domain.model.Role
import dev.projectvyuh.solo.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Append a user message to [conversation] and stream the assistant reply.
 *
 * Emits one [GenerationEvent] per token (or burst of tokens) so the ViewModel
 * can render the assistant message growing in real time. Terminal event is
 * [GenerationEvent.Done] (success) or [GenerationEvent.Failed].
 */
class SendMessageUseCase @Inject constructor(
    private val llm: LlmRepository,
) {
    operator fun invoke(
        conversation: Conversation,
        userInput: String,
    ): Flow<GenerationEvent> = flow {
        // 1. Append the user turn.
        val userMessage = Message(role = Role.USER, content = userInput)
        val withUser = conversation.append(userMessage)

        // 2. Append a placeholder assistant message that we grow as tokens arrive.
        var assistant = Message(role = Role.ASSISTANT, content = "", isStreaming = true)
        var current = withUser.append(assistant)
        emit(GenerationEvent.TokenEmitted(current))

        // 3. Stream tokens.
        try {
            llm.generate(withUser).collect { piece ->
                assistant = assistant.copy(content = assistant.content + piece)
                current = current.replaceLast(assistant)
                emit(GenerationEvent.TokenEmitted(current))
            }
            // 4. Mark the assistant message complete.
            assistant = assistant.copy(isStreaming = false)
            current = current.replaceLast(assistant)
            emit(GenerationEvent.Done(current))
        } catch (t: Throwable) {
            assistant = assistant.copy(isStreaming = false)
            current = current.replaceLast(assistant)
            emit(GenerationEvent.Failed(current, t))
        }
    }
}
