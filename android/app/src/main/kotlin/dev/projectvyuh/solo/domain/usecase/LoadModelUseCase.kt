package dev.projectvyuh.solo.domain.usecase

import dev.projectvyuh.solo.core.model.ModelDefinition
import dev.projectvyuh.solo.core.model.ModelInstallState
import dev.projectvyuh.solo.core.model.ModelManager
import dev.projectvyuh.solo.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Make a model ready for inference. Downloads if necessary, then loads.
 *
 * Emits the same [ModelInstallState] stream that ModelManager produces, with
 * one extra terminal value: once [ModelInstallState.Installed] is reached,
 * we additionally load the model into the engine and re-emit Installed so
 * the caller knows it's truly ready (file on disk AND resident in memory).
 */
class LoadModelUseCase @Inject constructor(
    private val modelManager: ModelManager,
    private val llm: LlmRepository,
) {
    operator fun invoke(model: ModelDefinition): Flow<ModelInstallState> = flow {
        // If it's already installed, skip straight to the load step.
        val current = modelManager.stateOf(model)
        if (current is ModelInstallState.Installed) {
            if (llm.activeModel?.id != model.id) llm.loadModel(model)
            emit(ModelInstallState.Installed(current.absolutePath))
            return@flow
        }

        // Otherwise: download → verify → load.
        modelManager.install(model).collect { state ->
            emit(state)
            if (state is ModelInstallState.Installed) {
                if (llm.activeModel?.id != model.id) llm.loadModel(model)
            }
        }
    }.flowOn(Dispatchers.IO)
}
