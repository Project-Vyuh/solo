package dev.projectvyuh.solo.presentation.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.domain.model.Message
import dev.projectvyuh.solo.presentation.chat.markdown.SoloStreamingMarkdown

/**
 * Solo's reply. No bubble — plain text on background. Markdown rendered
 * incrementally; a [StreamingCursor] tails the content while [Message.isStreaming].
 */
@Composable
fun AssistantMessage(message: Message, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(end = 24.dp)) {
        SoloStreamingMarkdown(text = message.content)
        if (message.isStreaming) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                StreamingCursor()
            }
        }
    }
}
