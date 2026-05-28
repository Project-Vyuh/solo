package dev.projectvyuh.solo.core.model

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.projectvyuh.solo.domain.repository.LlmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared state + lifecycle for the model download.
 *
 * The actual byte-pushing happens inside [dev.projectvyuh.solo.core.model.ModelDownloadService]
 * (so it survives app backgrounding). This controller is the single source of
 * truth that both the service and any observing ViewModel read from.
 */
@Singleton
class ModelDownloadController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val llm: LlmRepository,
) {
    private val _state = MutableStateFlow<ModelInstallState>(ModelInstallState.NotInstalled)
    val state: StateFlow<ModelInstallState> = _state.asStateFlow()

    @Volatile var active: ModelDefinition? = null
        private set

    /** Start (or resume) the download as a foreground service. Idempotent. */
    fun startDownload(model: ModelDefinition) {
        if (_state.value is ModelInstallState.Downloading) return
        active = model
        _state.value = modelManager.stateOf(model)
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /** Called by the service while downloading. */
    internal fun updateState(state: ModelInstallState) {
        _state.value = state
    }

    /**
     * Called after the file is verified. Loads the model into the engine so
     * chat is ready immediately when onboarding completes.
     */
    internal suspend fun loadIntoEngine(model: ModelDefinition) {
        try {
            llm.loadModel(model)
        } catch (t: Throwable) {
            _state.value = ModelInstallState.Failed("model loaded but engine init failed: ${t.message}", t)
        }
    }
}
