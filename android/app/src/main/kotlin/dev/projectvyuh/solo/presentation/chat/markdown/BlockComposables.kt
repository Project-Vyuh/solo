package dev.projectvyuh.solo.presentation.chat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloBorder
import dev.projectvyuh.solo.presentation.theme.SoloCodeStyle
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground
import dev.projectvyuh.solo.presentation.theme.SoloSurfaceElevated

@Composable
fun ParagraphBlock(block: MarkdownBlock.Paragraph, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val annotated = remember(block.inlines, onSurface) {
        InlineRenderer.render(
            inlines             = block.inlines,
            linkColor           = SoloAccent,
            codeBackgroundColor = SoloSurfaceElevated,
            codeForegroundColor = onSurface,
        )
    }
    Text(
        text     = annotated,
        style    = MaterialTheme.typography.bodyLarge,
        color    = onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun HeadingBlock(block: MarkdownBlock.Heading, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val annotated = remember(block.inlines, onSurface) {
        InlineRenderer.render(
            inlines             = block.inlines,
            linkColor           = SoloAccent,
            codeBackgroundColor = SoloSurfaceElevated,
            codeForegroundColor = onSurface,
        )
    }
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        3 -> MaterialTheme.typography.headlineSmall
        4 -> MaterialTheme.typography.titleLarge
        5 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Text(
        text     = annotated,
        style    = style,
        color    = onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CodeBlockBlock(block: MarkdownBlock.CodeBlock, modifier: Modifier = Modifier) {
    val horizontalScroll = rememberScrollState()
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SoloSurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, SoloBorder, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
    ) {
        if (block.language.isNotBlank()) {
            Text(
                text  = block.language,
                style = MaterialTheme.typography.labelSmall,
                color = SoloMutedForeground,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Text(
            text  = block.code,
            style = SoloCodeStyle,
            color = onSurface,
            softWrap = false,
            modifier = Modifier.horizontalScroll(horizontalScroll),
        )
    }
}

@Composable
fun ListBlockBlock(block: MarkdownBlock.ListBlock, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier.fillMaxWidth()) {
        block.items.forEachIndexed { idx, item ->
            val marker = if (block.ordered) "${idx + 1}." else "•"
            val annotated = remember(item.inlines, onSurface) {
                InlineRenderer.render(
                    inlines             = item.inlines,
                    linkColor           = SoloAccent,
                    codeBackgroundColor = SoloSurfaceElevated,
                    codeForegroundColor = onSurface,
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text     = marker,
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = SoloMutedForeground,
                    modifier = Modifier.width(24.dp),
                )
                Text(
                    text  = annotated,
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurface,
                )
            }
        }
    }
}

@Composable
fun QuoteBlock(block: MarkdownBlock.Quote, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val annotated = remember(block.inlines, onSurface) {
        InlineRenderer.render(
            inlines             = block.inlines,
            linkColor           = SoloAccent,
            codeBackgroundColor = SoloSurfaceElevated,
            codeForegroundColor = onSurface,
        )
    }
    Row(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .background(SoloAccent),
        ) { Spacer(modifier = Modifier.height(0.dp)) }
        Text(
            text  = annotated,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = SoloMutedForeground,
                fontWeight = FontWeight.Normal,
            ),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
fun DividerBlock(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SoloBorder),
    )
}
