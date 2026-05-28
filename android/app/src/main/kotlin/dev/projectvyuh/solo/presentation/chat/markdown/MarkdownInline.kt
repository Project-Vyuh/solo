package dev.projectvyuh.solo.presentation.chat.markdown

/**
 * Inline markdown span: the contents inside a paragraph, heading, or list item.
 *
 * The inline parser ([InlineParser]) tokenizes a String into a sequence of
 * these. Unlike block parsing, inline parsing has to be re-done whenever its
 * containing block's text changes — but inlines are cheap and the containing
 * block's text is short (one paragraph at most).
 */
sealed class MarkdownInline {
    data class Text(val value: String)            : MarkdownInline()
    data class Bold(val children: List<MarkdownInline>) : MarkdownInline()
    data class Italic(val children: List<MarkdownInline>) : MarkdownInline()
    data class Code(val value: String)            : MarkdownInline()
    data class Link(val text: String, val href: String) : MarkdownInline()

    /**
     * An *unclosed* span — used optimistically while streaming. The model has
     * emitted the opening delimiter but the closing one hasn't arrived yet. We
     * render with the implied style, accepting a small visual flicker if the
     * model never closes it (e.g., literal `**foo` with no follow-up `**`).
     */
    data class OpenSpan(
        val kind: Kind,
        val text: String,                         // raw text after the opening marker
    ) : MarkdownInline() {
        enum class Kind { BOLD, ITALIC, CODE }
    }
}
