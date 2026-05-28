package dev.projectvyuh.solo.presentation.chat

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.projectvyuh.solo.presentation.chat.components.ChatInput
import dev.projectvyuh.solo.presentation.chat.components.MessageList
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloBackground
import dev.projectvyuh.solo.presentation.theme.SoloBorder
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            isGenerating  = state.isGenerating,
            isModelReady  = state.isModelReady,
        )
    }
}
