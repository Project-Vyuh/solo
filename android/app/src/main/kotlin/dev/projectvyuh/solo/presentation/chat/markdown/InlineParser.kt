package dev.projectvyuh.solo.presentation.chat.markdown

/**
 * Parses a single block's text into inline spans.
 *
 * Supports:
 *   **bold**, *italic*, _italic_, `inline code`, [text](url)
 *
 * Optimistic streaming: if the input ends with an unclosed opening delimiter
 * (e.g., `... and **foo`), the trailing portion is emitted as an
 * [MarkdownInline.OpenSpan] so the UI can render it with the in-progress style.
 *
 * Strategy: simple left-to-right state machine. Not CommonMark-spec-complete
 * — handles the patterns LLMs actually produce. Edge cases around nested
 * `**_..._**` work in the common case (outer first, then inner) but extreme
 * nesting is not guaranteed.
 */
object InlineParser {

    fun parse(text: String): List<MarkdownInline> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<MarkdownInline>()
        var i = 0
        val buf = StringBuilder()

        fun flushText() {
            if (buf.isNotEmpty()) {
                out += MarkdownInline.Text(buf.toString())
                buf.setLength(0)
            }
        }

        while (i < text.length) {
            val c = text[i]

            // ---- inline code: `...` (or ``...`` for content containing `) ----
            if (c == '`') {
                // count leading backticks
                var run = 0
                while (i + run < text.length && text[i + run] == '`') run++
                val opener = "`".repeat(run)
                val close = text.indexOf(opener, startIndex = i + run)
                if (close >= 0) {
                    flushText()
                    val content = text.substring(i + run, close)
                    out += MarkdownInline.Code(content)
                    i = close + run
                    continue
                } else {
                    // unclosed — emit as open span if it's the tail, else literal
                    val remainder = text.substring(i + run)
                    if (!remainder.contains('\n')) {
                        flushText()
                        out += MarkdownInline.OpenSpan(MarkdownInline.OpenSpan.Kind.CODE, remainder)
                        return out
                    }
                    // not the tail — treat as literal
                    buf.append(c); i++; continue
                }
            }

            // ---- bold: **...** (must come BEFORE italic check) ----
            if (c == '*' && i + 1 < text.length && text[i + 1] == '*') {
                val close = text.indexOf("**", startIndex = i + 2)
                if (close >= 0) {
                    flushText()
                    val inner = text.substring(i + 2, close)
                    out += MarkdownInline.Bold(parse(inner))
                    i = close + 2
                    continue
                } else if (i + 2 < text.length) {
                    // opening present, no close yet — optimistic open span
                    flushText()
                    out += MarkdownInline.OpenSpan(
                        MarkdownInline.OpenSpan.Kind.BOLD,
                        text.substring(i + 2),
                    )
                    return out
                } else {
                    buf.append(c); i++; continue
                }
            }

            // ---- italic: *...* or _..._ ----
            if (c == '*' || c == '_') {
                val opener = c.toString()
                // For asterisk-italic, require non-space after to avoid eating bullet-list markers
                val nextIsSpace = i + 1 >= text.length || text[i + 1].isWhitespace()
                if (nextIsSpace) {
                    buf.append(c); i++; continue
                }
                val close = text.indexOf(opener, startIndex = i + 1)
                if (close >= 0 && !text[close - 1].isWhitespace()) {
                    flushText()
                    val inner = text.substring(i + 1, close)
                    out += MarkdownInline.Italic(parse(inner))
                    i = close + 1
                    continue
                } else {
                    // unclosed
                    flushText()
                    out += MarkdownInline.OpenSpan(
                        MarkdownInline.OpenSpan.Kind.ITALIC,
                        text.substring(i + 1),
                    )
                    return out
                }
            }

            // ---- link: [text](url) ----
            if (c == '[') {
                val textClose = text.indexOf(']', startIndex = i + 1)
                if (textClose > 0 && textClose + 1 < text.length && text[textClose + 1] == '(') {
                    val urlClose = text.indexOf(')', startIndex = textClose + 2)
                    if (urlClose > 0) {
                        flushText()
                        val linkText = text.substring(i + 1, textClose)
                        val href = text.substring(textClose + 2, urlClose)
                        out += MarkdownInline.Link(linkText, href)
                        i = urlClose + 1
                        continue
                    }
                }
                // not a valid link — literal `[`
                buf.append(c); i++; continue
            }

            buf.append(c)
            i++
        }

        flushText()
        return out
    }
}
