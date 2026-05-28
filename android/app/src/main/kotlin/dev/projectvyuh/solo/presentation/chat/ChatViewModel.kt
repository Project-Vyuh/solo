package dev.projectvyuh.solo.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.projectvyuh.solo.domain.model.Conversation
import dev.projectvyuh.solo.domain.model.Message
import dev.projectvyuh.solo.domain.model.Role
import dev.projectvyuh.solo.domain.persona.SoloSystemPrompt
import dev.projectvyuh.solo.domain.repository.LlmRepository
import dev.projectvyuh.solo.domain.usecase.GenerationEvent
import dev.projectvyuh.solo.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the chat screen.
 *
 * Holds a single in-memory [Conversation]. Persistence (Room) is deferred to a
 * later phase; for Phase 1A, restarting the app starts a new conversation.
 */
data class ChatUiState(
    val conversation: Conversation = Conversation(messages = listOf(SYSTEM_PROMPT)),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val isModelReady: Boolean = false,
    val error: String? = null,
) {
    companion object {
        // Single source of truth for Solo's prompt lives in SoloSystemPrompt.
        // Built once at object init; rebuilt only when persona/phase changes.
        val SYSTEM_PROMPT = Message(
            role = Role.SYSTEM,
            content = SoloSystemPrompt.build(),
        )
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessage: SendMessageUseCase,
    private val llm: LlmRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(isModelReady = llm.isReady))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var generationJob: Job? = null

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    fun onSend() {
        val current = _state.value
        val text = current.inputText.trim()
        if (text.isEmpty() || current.isGenerating) return
        if (!llm.isReady) {
            _state.update { it.copy(error = "Model not loaded yet") }
            return
        }

        _state.update {
            it.copy(inputText = "", isGenerating = true, error = null)
        }

        generationJob = viewModelScope.launch {
            sendMessage(_state.value.conversation, text).collect { event ->
                when (event) {
                    is GenerationEvent.TokenEmitted -> {
                        _state.update { it.copy(conversation = event.conversation) }
                    }
                    is GenerationEvent.Done -> {
                        _state.update {
                            it.copy(conversation = event.conversation, isGenerating = false)
                        }
                    }
                    is GenerationEvent.Failed -> {
                        _state.update {
                            it.copy(
                                conversation = event.conversation,
                                isGenerating = false,
                                error = event.cause.message ?: "generation failed",
                            )
                        }
                    }
                }
            }
        }
    }

    fun onAbort() {
        llm.abort()
        generationJob?.cancel()
        _state.update { it.copy(isGenerating = false) }
    }

    fun onErrorDismiss() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Notify the VM that the model has finished loading. Called from the
     * onboarding flow when the GGUF is verified + loaded into the engine.
     */
    fun onModelReady() {
        _state.update { it.copy(isModelReady = llm.isReady) }
    }
}
