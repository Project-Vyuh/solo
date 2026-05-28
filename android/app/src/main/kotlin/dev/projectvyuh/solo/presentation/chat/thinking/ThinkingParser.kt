package dev.projectvyuh.solo.presentation.chat.thinking

/**
 * Splits a (possibly mid-stream) assistant message into a sequence of
 * thinking segments and response segments.
 *
 * Supports two thinking-format conventions:
 *
 *   1. Gemma 4 native channel format (primary, since Gemma 4 is Solo's
 *      default model):
 *        <|channel>thought
 *        ...reasoning...
 *        <channel|>
 *
 *   2. Generic <think>...</think> tags (fallback for DeepSeek-R1, Qwen3,
 *      and any other model whose output we route through this parser).
 *
 * The parser is robust to incomplete output: an unclosed thinking block has
 * isClosed=false so the UI can render the "still thinking…" affordance.
 */
object ThinkingParser {

    sealed interface Segment {
        val text: String

        /** Reasoning content inside a thinking block. */
        data class Thinking(override val text: String, val isClosed: Boolean) : Segment

        /** Everything else — the user-facing answer. */
        data class Response(override val text: String) : Segment
    }

    /** Pairs of (opening marker, closing marker) the parser knows about. */
    private val MARKERS = listOf(
        // Gemma 4 native — exact strings from Google's docs.
        Marker("<|channel>thought", "<channel|>"),
        // Generic.
        Marker("<think>", "</think>"),
    )

    private data class Marker(val open: String, val close: String)
    private data class MatchedOpen(val index: Int, val marker: Marker)

    fun parse(message: String): List<Segment> {
        if (message.isEmpty()) return emptyList()
        val out = mutableListOf<Segment>()
        var i = 0
        while (i < message.length) {
            val opener = findEarliestOpener(message, i)
            if (opener == null) {
                appendResponse(out, message.substring(i))
                break
            }
            if (opener.index > i) {
                appendResponse(out, message.substring(i, opener.index))
            }
            val afterOpen = opener.index + opener.marker.open.length
            val closeIdx = message.indexOf(opener.marker.close, startIndex = afterOpen)
            if (closeIdx < 0) {
                out += Segment.Thinking(
                    text = message.substring(afterOpen).trimStart('\n', ' '),
                    isClosed = false,
                )
                break
            }
            out += Segment.Thinking(
                text = message.substring(afterOpen, closeIdx).trim('\n', ' '),
                isClosed = true,
            )
            i = closeIdx + opener.marker.close.length
            if (i < message.length && message[i] == '\n') i++
        }
        return out
    }

    /** Find the next opening marker (from any known format) at or after [from]. */
    private fun findEarliestOpener(message: String, from: Int): MatchedOpen? {
        var best: MatchedOpen? = null
        for (m in MARKERS) {
            val idx = message.indexOf(m.open, startIndex = from)
            if (idx < 0) continue
            if (best == null || idx < best.index) best = MatchedOpen(idx, m)
        }
        return best
    }

    private fun appendResponse(out: MutableList<Segment>, text: String) {
        if (text.isEmpty()) return
        val last = out.lastOrNull()
        if (last is Segment.Response) {
            out[out.lastIndex] = Segment.Response(last.text + text)
        } else {
            out += Segment.Response(text)
        }
    }
}
