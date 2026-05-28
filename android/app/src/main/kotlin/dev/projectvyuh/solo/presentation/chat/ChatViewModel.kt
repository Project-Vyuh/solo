package dev.projectvyuh.solo.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.projectvyuh.solo.core.model.ModelInstallState
import dev.projectvyuh.solo.core.model.ModelManager
import dev.projectvyuh.solo.core.model.VoiceModelRegistry
import dev.projectvyuh.solo.data.voice.orchestration.VoiceOrchestrator
import dev.projectvyuh.solo.data.voice.orchestration.VoiceTurnState
import dev.projectvyuh.solo.domain.model.Conversation
import dev.projectvyuh.solo.domain.model.Message
import dev.projectvyuh.solo.domain.model.Role
import dev.projectvyuh.solo.domain.persona.SoloSystemPrompt
import dev.projectvyuh.solo.domain.repository.LlmRepository
import dev.projectvyuh.solo.domain.usecase.GenerationEvent
import dev.projectvyuh.solo.domain.usecase.SendMessageUseCase
import dev.projectvyuh.solo.presentation.chat.components.VoiceDownloadDialogState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the chat screen.
 */
data class ChatUiState(
    val conversation: Conversation = Conversation(messages = listOf(SYSTEM_PROMPT)),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val isModelReady: Boolean = false,
    val voiceTurn: VoiceTurnState = VoiceTurnState.Idle,
    val voiceDownloadDialog: VoiceDownloadDialogState? = null,
    val needsMicPermission: Boolean = false,
    val error: String? = null,
) {
    companion object {
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
    private val orchestrator: VoiceOrchestrator,
    private val modelManager: ModelManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(isModelReady = llm.isReady))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var generationJob: Job? = null

    init {
        // Pipe voice orchestrator state into UI state.
        viewModelScope.launch {
            orchestrator.state.collect { vs ->
                _state.update { it.copy(voiceTurn = vs) }
            }
        }
    }

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

    fun onModelReady() {
        _state.update { it.copy(isModelReady = llm.isReady) }
    }

    // -----------------------------------------------------------------------
    // Voice
    // -----------------------------------------------------------------------

    /**
     * Mic tap entry point. Branches on:
     *   1. Voice turn already in flight → abort
     *   2. Voice models not installed → show download dialog (opt-in)
     *   3. RECORD_AUDIO not granted → ask UI to request permission
     *   4. Ready → start the turn
     */
    fun onMicTap(hasMicPermission: Boolean) {
        val current = _state.value
        // (1) abort if active
        if (current.voiceTurn !is VoiceTurnState.Idle) {
            viewModelScope.launch { orchestrator.abort() }
            return
        }
        // (2) gate on voice models
        if (!orchestrator.areVoiceModelsInstalled) {
            _state.update { it.copy(voiceDownloadDialog = VoiceDownloadDialogState.Confirming) }
            return
        }
        // (3) gate on permission
        if (!hasMicPermission) {
            _state.update { it.copy(needsMicPermission = true) }
            return
        }
        // (4) go
        viewModelScope.launch {
            runCatching { orchestrator.startTurn() }
                .onFailure { _state.update { s -> s.copy(error = it.message ?: "voice start failed") } }
        }
    }

    fun onMicPermissionResult(granted: Boolean) {
        _state.update { it.copy(needsMicPermission = false) }
        if (granted) onMicTap(hasMicPermission = true)
    }

    fun onVoiceDownloadDismiss() {
        _state.update { it.copy(voiceDownloadDialog = null) }
    }

    /**
     * Sequentially install all three voice models via the existing
     * [ModelManager], updating the dialog state as we go.
     */
    fun onVoiceDownloadStart() {
        viewModelScope.launch {
            val models = VoiceModelRegistry.all
            val totalBytes = VoiceModelRegistry.totalBytes
            var bytesDoneAcrossModels = 0L

            for (model in models) {
                if (modelManager.isInstalled(model)) {
                    bytesDoneAcrossModels += model.sizeBytes
                    continue
                }
                val currentLabel = model.displayName
                modelManager.install(model).collect { s ->
                    when (s) {
                        is ModelInstallState.Downloading -> {
                            val cumulativeBytes = bytesDoneAcrossModels + s.bytesDownloaded
                            _state.update {
                                it.copy(
                                    voiceDownloadDialog = VoiceDownloadDialogState.Downloading(
                                        currentModel = currentLabel,
                                        downloadedMb = cumulativeBytes / 1_000_000,
                                        totalMb      = totalBytes / 1_000_000,
                                        fraction     = cumulativeBytes.toFloat() / totalBytes,
                                    )
                                )
                            }
                        }
                        ModelInstallState.Verifying -> Unit
                        is ModelInstallState.Installed -> Unit
                        is ModelInstallState.Failed -> {
                            _state.update {
                                it.copy(voiceDownloadDialog = VoiceDownloadDialogState.Failed(s.message))
                            }
                            return@collect
                        }
                        is ModelInstallState.PartiallyDownloaded,
                        ModelInstallState.NotInstalled -> Unit
                    }
                }
                bytesDoneAcrossModels += model.sizeBytes
            }

            // All done — dismiss the dialog. The next mic tap proceeds normally.
            _state.update { it.copy(voiceDownloadDialog = null) }
        }
    }
}
