package dev.projectvyuh.solo.presentation.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.domain.model.Message
import dev.projectvyuh.solo.presentation.chat.markdown.SoloStreamingMarkdown
import dev.projectvyuh.solo.presentation.chat.thinking.ThinkingParser
import dev.projectvyuh.solo.presentation.chat.thinking.ThinkingSection

/**
 * Solo's reply.
 *
 * Pipeline per message:
 *   raw text → ThinkingParser → segments (Thinking / Response)
 *               → each Thinking → ThinkingSection (collapsible)
 *               → each Response → SoloStreamingMarkdown (block-incremental)
 *
 * The streaming cursor renders at the very end while [Message.isStreaming].
 */
@Composable
fun AssistantMessage(message: Message, modifier: Modifier = Modifier) {
    val segments = remember(message.content) { ThinkingParser.parse(message.content) }

    Column(
        modifier = modifier.fillMaxWidth().padding(end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is ThinkingParser.Segment.Thinking -> ThinkingSection(segment)
                is ThinkingParser.Segment.Response -> SoloStreamingMarkdown(text = segment.text)
            }
        }
        if (message.isStreaming) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                StreamingCursor()
            }
        }
    }
}
