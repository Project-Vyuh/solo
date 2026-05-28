package dev.projectvyuh.solo.presentation.shell.gating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.projectvyuh.solo.core.model.ModelManager
import dev.projectvyuh.solo.core.model.ModelRegistry
import dev.projectvyuh.solo.domain.repository.LlmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SoloRootUiState(
    val modelReady: Boolean = false,
)

/**
 * Decides whether the app starts in onboarding or in the main shell.
 *
 * Phase 1A rule: enter the main shell iff the primary model is installed AND
 * loaded into the engine. On first launch this is false → onboarding.
 * On subsequent launches this is true once the file is on disk (we eagerly
 * load it at startup) → straight to chat.
 */
@HiltViewModel
class SoloRootViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val llm: LlmRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SoloRootUiState())
    val state: StateFlow<SoloRootUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val primary = ModelRegistry.primary
            if (modelManager.isInstalled(primary)) {
                if (!llm.isReady) {
                    runCatching { llm.loadModel(primary) }
                }
                _state.update { it.copy(modelReady = llm.isReady) }
            }
        }
    }

    fun markOnboardingComplete() {
        _state.update { it.copy(modelReady = llm.isReady) }
    }
}
