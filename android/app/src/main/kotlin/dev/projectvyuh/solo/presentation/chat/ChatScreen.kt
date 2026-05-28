package dev.projectvyuh.solo.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.projectvyuh.solo.data.voice.orchestration.VoiceTurnState
import dev.projectvyuh.solo.presentation.chat.components.ChatInput
import dev.projectvyuh.solo.presentation.chat.components.MessageList
import dev.projectvyuh.solo.presentation.chat.components.VoiceButtonState
import dev.projectvyuh.solo.presentation.chat.components.VoiceDownloadDialog
import dev.projectvyuh.solo.presentation.chat.components.VoiceTurnIndicator
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloBackground
import dev.projectvyuh.solo.presentation.theme.SoloBorder
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Runtime mic permission via Activity Result API.
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onMicPermissionResult(granted)
    }

    LaunchedEffect(state.needsMicPermission) {
        if (state.needsMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoloBackground),
    ) {
        // ---- compact header ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (state.isModelReady) SoloAccent else SoloMutedForeground,
                        shape = CircleShape,
                    ),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text  = "Solo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ---- voice turn indicator (visible only when non-Idle) ----
        VoiceTurnIndicator(state = state.voiceTurn)

        // ---- messages ----
        Box(modifier = Modifier.weight(1f)) {
            MessageList(conversation = state.conversation)
        }

        // ---- divider + input ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SoloBorder),
        )
        ChatInput(
            value         = state.inputText,
            onValueChange = viewModel::onInputChange,
            onSend        = viewModel::onSend,
            onStop        = viewModel::onAbort,
            onMicTap      = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                viewModel.onMicTap(hasMicPermission = granted)
            },
            voiceButtonState = voiceButtonStateFor(state),
            isGenerating  = state.isGenerating,
            isModelReady  = state.isModelReady,
        )
    }

    state.voiceDownloadDialog?.let { dialogState ->
        VoiceDownloadDialog(
            state     = dialogState,
            onConfirm = viewModel::onVoiceDownloadStart,
            onDismiss = viewModel::onVoiceDownloadDismiss,
        )
    }
}

private fun voiceButtonStateFor(state: ChatUiState): VoiceButtonState =
    when {
        !state.isModelReady       -> VoiceButtonState.Disabled
        state.voiceTurn !is VoiceTurnState.Idle -> VoiceButtonState.Active
        else                       -> VoiceButtonState.Idle
    }
