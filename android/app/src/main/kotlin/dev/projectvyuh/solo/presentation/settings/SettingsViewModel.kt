package dev.projectvyuh.solo.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.projectvyuh.solo.core.model.ModelDefinition
import dev.projectvyuh.solo.core.model.ModelManager
import dev.projectvyuh.solo.core.model.ModelRegistry
import dev.projectvyuh.solo.core.thermal.ThermalMonitor
import dev.projectvyuh.solo.data.llm.LlamaCppEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val activeModel: ModelDefinition? = null,
    val modelInstalled: Boolean = false,
    val modelSizeBytes: Long = 0L,
    val thermal: ThermalMonitor.Level = ThermalMonitor.Level.NONE,
    val nativeVersion: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val modelManager: ModelManager,
    private val engine: LlamaCppEngine,
    thermalMonitor: ThermalMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            activeModel    = ModelRegistry.primary,
            modelInstalled = modelManager.isInstalled(ModelRegistry.primary),
            modelSizeBytes = modelManager.modelFile(ModelRegistry.primary).takeIf { it.exists() }?.length() ?: 0,
            thermal        = thermalMonitor.currentLevel(),
            nativeVersion  = runCatching { engine.nativeVersion() }.getOrDefault(""),
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            thermalMonitor.observe().collect { level ->
                _state.update { it.copy(thermal = level) }
            }
        }
    }
}
