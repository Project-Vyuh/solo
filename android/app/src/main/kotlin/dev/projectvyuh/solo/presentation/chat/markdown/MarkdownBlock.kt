package dev.projectvyuh.solo.presentation.chat.markdown

/**
 * One closed (or in-progress) block of markdown.
 *
 * Blocks are the unit of incremental parsing in [BlockParser]: once a block is
 * closed it is never re-parsed. Only the trailing "open" block continues to
 * accept new tokens.
 *
 * [key] is a stable identifier used as the Compose key for this block, so
 * recomposition can stop at the block boundary instead of walking the entire
 * message tree on every streamed token.
 */
sealed class MarkdownBlock {
    abstract val key: String

    /** A paragraph of text. May contain inline markdown (bold, italic, code). */
    data class Paragraph(
        override val key: String,
        val inlines: List<MarkdownInline>,
    ) : MarkdownBlock()

    /** ATX heading: `# foo` through `###### foo`. */
    data class Heading(
        override val key: String,
        val level: Int,                       // 1..6
        val inlines: List<MarkdownInline>,
    ) : MarkdownBlock()

    /** Fenced code block: ```lang\n...\n```. */
    data class CodeBlock(
        override val key: String,
        val language: String,                 // empty string if no language given
        val code: String,
        val isClosed: Boolean,                // false while the closing fence hasn't arrived
    ) : MarkdownBlock()

    /** A run of bullet- or numbered-list items at one nesting level. */
    data class ListBlock(
        override val key: String,
        val ordered: Boolean,
        val items: List<ListItem>,
    ) : MarkdownBlock()

    data class ListItem(
        val marker: String,                   // "-", "*", "1.", etc — for ordered rendering
        val inlines: List<MarkdownInline>,
    )

    /** Block quote: `> foo`. */
    data class Quote(
        override val key: String,
        val inlines: List<MarkdownInline>,
    ) : MarkdownBlock()

    /** Horizontal rule: `---` / `***` / `___`. */
    data class Divider(
        override val key: String,
    ) : MarkdownBlock()
}
