package dev.projectvyuh.solo.presentation.chat.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Renders a String of (possibly mid-stream) markdown.
 *
 * Architecture: text → blocks via [BlockParser] (memoized on text); each block
 * is rendered by a dedicated composable keyed by [MarkdownBlock.key]. Compose
 * uses these keys to skip recomposition for blocks whose data did not change
 * — so adding tokens to the trailing open block does NOT cause earlier blocks
 * to re-render.
 *
 * For streaming UX, the last block is the "open" block; if it's a paragraph
 * with an unclosed bold/italic/code span, the [InlineParser] emits an
 * [MarkdownInline.OpenSpan] that the renderer styles optimistically.
 */
@Composable
fun SoloStreamingMarkdown(
    text: String,
    modifier: Modifier = Modifier,
) {
    val parser = remember { BlockParser() }
    val blocks = remember(text) { parser.parse(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEach { block ->
            // The key here is what makes recomposition skip past closed blocks
            // whose data hasn't changed across token arrivals.
            androidx.compose.runtime.key(block.key) {
                when (block) {
                    is MarkdownBlock.Paragraph -> ParagraphBlock(block)
                    is MarkdownBlock.Heading   -> HeadingBlock(block)
                    is MarkdownBlock.CodeBlock -> CodeBlockBlock(block)
                    is MarkdownBlock.ListBlock -> ListBlockBlock(block)
                    is MarkdownBlock.Quote     -> QuoteBlock(block)
                    is MarkdownBlock.Divider   -> DividerBlock()
                }
            }
        }
    }
}
