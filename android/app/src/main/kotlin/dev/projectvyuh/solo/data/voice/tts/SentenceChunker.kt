package dev.projectvyuh.solo.data.voice.tts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Splits a streaming LLM token sequence into sentence-sized chunks for TTS.
 *
 * Sentence boundaries fire on:
 *   - `.` `!` `?` followed by whitespace or end-of-stream
 *   - newline characters (paragraph boundary)
 *   - explicit fence after [maxChars] characters even without punctuation
 *     (handles enumeration-heavy responses)
 *
 * Why: TTS engines synthesize one sentence at a time. Buffering tokens until
 * a complete sentence appears lets us start audio playback ~one sentence
 * worth of latency after the LLM begins generating, rather than waiting for
 * the entire response. This is the core of the streaming-overlap pattern
 * described in SOLO-VOICE.md §2.2.
 *
 * Markdown stripping is intentional and aggressive — the user is going to
 * HEAR this, so bold markers, code fences, list bullets, headings, etc.
 * should not be voiced as literal punctuation.
 */
class SentenceChunker(private val maxChars: Int = 200) {

    /**
     * Consume a token stream from the LLM and emit one chunk per complete
     * sentence. Final partial chunk (if any) is emitted on collector close.
     */
    fun chunk(tokens: Flow<String>): Flow<String> = flow {
        val buf = StringBuilder()
        tokens.collect { piece ->
            buf.append(piece)
            while (true) {
                val boundary = findSentenceBoundary(buf)
                if (boundary < 0) break
                val raw = buf.substring(0, boundary + 1)
                buf.delete(0, boundary + 1)
                val cleaned = stripMarkdownForSpeech(raw).trim()
                if (cleaned.isNotEmpty()) emit(cleaned)
            }
            // Force-flush if buffer grew too large without a sentence boundary
            if (buf.length >= maxChars) {
                val cleaned = stripMarkdownForSpeech(buf.toString()).trim()
                buf.clear()
                if (cleaned.isNotEmpty()) emit(cleaned)
            }
        }
        // Flush remainder at stream end
        if (buf.isNotEmpty()) {
            val cleaned = stripMarkdownForSpeech(buf.toString()).trim()
            if (cleaned.isNotEmpty()) emit(cleaned)
        }
    }

    /** Index of the first sentence-terminator in [sb], or -1. */
    private fun findSentenceBoundary(sb: StringBuilder): Int {
        for (i in sb.indices) {
            val c = sb[i]
            if (c == '\n') return i
            if (c == '.' || c == '!' || c == '?') {
                // Require trailing whitespace or end to avoid splitting "3.14" or "etc."
                if (i == sb.length - 1) continue           // wait for next char
                val next = sb[i + 1]
                if (next.isWhitespace()) return i
            }
        }
        return -1
    }

    /**
     * Strip markdown that doesn't translate to speech:
     *   - `**bold**` / `*italic*` markers → keep text, drop markers
     *   - `` `code` `` → keep text, drop backticks
     *   - ```` ``` ```` code fences → drop entirely
     *   - `# heading` → strip leading hashes
     *   - `- ` / `* ` / `1. ` list markers → strip
     *   - `[text](url)` → keep text, drop URL
     */
    private fun stripMarkdownForSpeech(text: String): String {
        var s = text
        // Code fences: drop the fenced content entirely (don't try to read code)
        s = s.replace(Regex("""```[\s\S]*?```"""), "")
        // Inline code: keep content
        s = s.replace(Regex("""`([^`]*)`"""), "$1")
        // Bold/italic markers
        s = s.replace(Regex("""\*\*(.*?)\*\*"""), "$1")
        s = s.replace(Regex("""\*(.*?)\*"""), "$1")
        s = s.replace(Regex("""_(.*?)_"""), "$1")
        // Links: text only
        s = s.replace(Regex("""\[([^\]]*)\]\([^)]*\)"""), "$1")
        // Heading markers at line start
        s = s.replace(Regex("""(?m)^#{1,6}\s+"""), "")
        // List bullets / numbered list markers at line start
        s = s.replace(Regex("""(?m)^\s*[-*+]\s+"""), "")
        s = s.replace(Regex("""(?m)^\s*\d+\.\s+"""), "")
        // Collapse repeated whitespace
        s = s.replace(Regex("""\s+"""), " ")
        return s
    }
}
