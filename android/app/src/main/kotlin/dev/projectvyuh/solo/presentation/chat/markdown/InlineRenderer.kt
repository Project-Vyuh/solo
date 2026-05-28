package dev.projectvyuh.solo.presentation.chat.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color

/**
 * Converts inline markdown spans into a Compose [AnnotatedString].
 *
 * Pure function — given the same input, produces the same output. This makes
 * it safe to call inside `remember(text)` blocks; downstream Composables only
 * re-render when the inline list actually changes.
 */
object InlineRenderer {

    fun render(
        inlines: List<MarkdownInline>,
        linkColor: Color,
        codeBackgroundColor: Color,
        codeForegroundColor: Color,
    ): AnnotatedString = buildAnnotatedString {
        renderInto(this, inlines, linkColor, codeBackgroundColor, codeForegroundColor)
    }

    private fun renderInto(
        builder: androidx.compose.ui.text.AnnotatedString.Builder,
        inlines: List<MarkdownInline>,
        linkColor: Color,
        codeBackgroundColor: Color,
        codeForegroundColor: Color,
    ) {
        inlines.forEach { inline ->
            when (inline) {
                is MarkdownInline.Text -> builder.append(inline.value)

                is MarkdownInline.Bold -> builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    renderInto(builder, inline.children, linkColor, codeBackgroundColor, codeForegroundColor)
                }

                is MarkdownInline.Italic -> builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    renderInto(builder, inline.children, linkColor, codeBackgroundColor, codeForegroundColor)
                }

                is MarkdownInline.Code -> builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackgroundColor,
                        color      = codeForegroundColor,
                    )
                ) { builder.append(inline.value) }

                is MarkdownInline.Link -> builder.withStyle(
                    SpanStyle(color = linkColor)
                ) { builder.append(inline.text) }

                // Optimistic open spans — render with the in-progress style.
                is MarkdownInline.OpenSpan -> when (inline.kind) {
                    MarkdownInline.OpenSpan.Kind.BOLD ->
                        builder.withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            builder.append(inline.text)
                        }
                    MarkdownInline.OpenSpan.Kind.ITALIC ->
                        builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            builder.append(inline.text)
                        }
                    MarkdownInline.OpenSpan.Kind.CODE ->
                        builder.withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackgroundColor,
                                color      = codeForegroundColor,
                            )
                        ) { builder.append(inline.text) }
                }
            }
        }
    }
}
