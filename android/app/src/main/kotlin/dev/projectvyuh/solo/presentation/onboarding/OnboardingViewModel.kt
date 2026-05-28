package dev.projectvyuh.solo.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.projectvyuh.solo.core.model.ModelDownloadController
import dev.projectvyuh.solo.core.model.ModelInstallState
import dev.projectvyuh.solo.core.model.ModelManager
import dev.projectvyuh.solo.core.model.ModelRegistry
import dev.projectvyuh.solo.domain.repository.LlmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Linear onboarding flow. Each step advances explicitly via user action; we
 * never auto-skip ahead because each step is a deliberate touchpoint.
 */
enum class OnboardingStep {
    WELCOME,
    PRIVACY,
    PERMISSIONS,
    DOWNLOAD,
    DONE,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val install: ModelInstallState = ModelInstallState.NotInstalled,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val downloadController: ModelDownloadController,
    private val modelManager: ModelManager,
    private val llm: LlmRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            downloadController.state.collect { s ->
                _state.update { it.copy(install = s) }
            }
        }
    }

    fun advance() {
        val next = when (_state.value.step) {
            OnboardingStep.WELCOME     -> OnboardingStep.PRIVACY
            OnboardingStep.PRIVACY     -> OnboardingStep.PERMISSIONS
            OnboardingStep.PERMISSIONS -> OnboardingStep.DOWNLOAD
            OnboardingStep.DOWNLOAD    -> OnboardingStep.DONE
            OnboardingStep.DONE        -> OnboardingStep.DONE
        }
        _state.update { it.copy(step = next) }
    }

    fun startModelDownload() {
        val model = ModelRegistry.primary
        // If already installed but not in engine, just load it.
        if (modelManager.isInstalled(model)) {
            viewModelScope.launch {
                if (!llm.isReady) llm.loadModel(model)
                _state.update {
                    it.copy(install = ModelInstallState.Installed(modelManager.modelFile(model).absolutePath))
                }
            }
            return
        }
        downloadController.startDownload(model)
    }

    val primaryModel get() = ModelRegistry.primary
}
