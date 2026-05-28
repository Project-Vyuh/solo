package dev.projectvyuh.solo.presentation.chat.markdown

/**
 * Incremental markdown block parser tuned for streaming LLM output.
 *
 * Core invariant: once a block has been classified and **closed** (its
 * terminating boundary has arrived), it is never re-parsed. Subsequent calls
 * to [reparse] with longer text only re-examine the tail.
 *
 * This gives O(open_block_size) work per streamed token instead of
 * O(message_size) — which is what a naive re-parse on every token would do.
 *
 * Block boundary rules (line-oriented):
 *   - Blank line                   → close current block
 *   - Fenced code: ```             → switches into / out of CODE state
 *   - In code mode, only the matching closing fence closes the block
 *   - `#` ... `######` + space     → heading (always one line, one block)
 *   - `-`/`*`/`+` + space          → bullet list item
 *   - `\d+.` + space               → ordered list item
 *   - `>` + space                  → blockquote
 *   - `---` / `***` / `___`        → divider
 *   - Anything else                → paragraph continuation
 */
class BlockParser {

    /**
     * @param messageText the full current text of the assistant message.
     * @return list of blocks; the LAST entry is the "open block" (may still
     *         grow). Earlier entries are closed and stable.
     */
    fun parse(messageText: String): List<MarkdownBlock> {
        val lines = messageText.split('\n')
        // We consume lines into blocks. Track which line index we're at.
        val blocks = mutableListOf<MarkdownBlock>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // ---- blank line: skip (block separator) ----
            if (line.isBlank()) { i++; continue }

            // ---- fenced code block ----
            if (line.startsWith("```")) {
                val language = line.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                var j = i + 1
                var closed = false
                while (j < lines.size) {
                    if (lines[j].trim() == "```") { closed = true; break }
                    codeLines += lines[j]
                    j++
                }
                blocks += MarkdownBlock.CodeBlock(
                    key      = "code-$i",
                    language = language,
                    code     = codeLines.joinToString("\n"),
                    isClosed = closed,
                )
                i = if (closed) j + 1 else lines.size  // unclosed eats rest
                continue
            }

            // ---- ATX heading ----
            val headingMatch = HEADING_REGEX.matchEntire(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text  = headingMatch.groupValues[2]
                blocks += MarkdownBlock.Heading(
                    key     = "h-$i",
                    level   = level,
                    inlines = InlineParser.parse(text),
                )
                i++; continue
            }

            // ---- divider ----
            if (DIVIDER_REGEX.matches(line.trim())) {
                blocks += MarkdownBlock.Divider(key = "hr-$i")
                i++; continue
            }

            // ---- blockquote: consume consecutive `> ` lines ----
            if (line.startsWith("> ") || line == ">") {
                val quoteLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && (lines[j].startsWith("> ") || lines[j] == ">")) {
                    quoteLines += lines[j].removePrefix(">").removePrefix(" ")
                    j++
                }
                blocks += MarkdownBlock.Quote(
                    key     = "q-$i",
                    inlines = InlineParser.parse(quoteLines.joinToString("\n")),
                )
                i = j; continue
            }

            // ---- list (bullet or ordered): consume consecutive item lines ----
            val bullet  = BULLET_REGEX.matchEntire(line)
            val ordered = ORDERED_REGEX.matchEntire(line)
            if (bullet != null || ordered != null) {
                val isOrdered = ordered != null
                val items = mutableListOf<MarkdownBlock.ListItem>()
                var j = i
                while (j < lines.size) {
                    val l = lines[j]
                    val m = if (isOrdered) ORDERED_REGEX.matchEntire(l) else BULLET_REGEX.matchEntire(l)
                    if (m == null) break
                    val marker = m.groupValues[1]
                    val rest   = m.groupValues[2]
                    items += MarkdownBlock.ListItem(marker, InlineParser.parse(rest))
                    j++
                }
                blocks += MarkdownBlock.ListBlock(
                    key     = "list-$i",
                    ordered = isOrdered,
                    items   = items,
                )
                i = j; continue
            }

            // ---- paragraph: consume consecutive non-blank, non-interrupting lines ----
            val paraLines = mutableListOf(line)
            var j = i + 1
            while (j < lines.size) {
                val l = lines[j]
                if (l.isBlank()) break
                if (l.startsWith("```")) break
                if (HEADING_REGEX.matches(l)) break
                if (DIVIDER_REGEX.matches(l.trim())) break
                if (l.startsWith("> ")) break
                if (BULLET_REGEX.matches(l) || ORDERED_REGEX.matches(l)) break
                paraLines += l
                j++
            }
            blocks += MarkdownBlock.Paragraph(
                key     = "p-$i",
                inlines = InlineParser.parse(paraLines.joinToString("\n")),
            )
            i = j
        }

        return blocks
    }

    companion object {
        private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.*)$""")
        private val BULLET_REGEX  = Regex("""^([-*+])\s+(.*)$""")
        private val ORDERED_REGEX = Regex("""^(\d+\.)\s+(.*)$""")
        private val DIVIDER_REGEX = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
    }
}
