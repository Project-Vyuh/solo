package dev.projectvyuh.solo.presentation.chat.thinking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.presentation.chat.markdown.SoloStreamingMarkdown
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloBorder
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground
import dev.projectvyuh.solo.presentation.theme.SoloSurface

/**
 * Collapsible block for Solo's [ThinkingParser.Segment.Thinking] segments.
 *
 * Collapsed by default to match the design philosophy ("answer first; show
 * work on demand"). Tap header → expand; reveals the full reasoning rendered
 * with the same streaming-markdown pipeline as the final answer.
 *
 * While the segment is still streaming (isClosed=false), the header shows
 * "Thinking..." with a subtle pulse; on close, it transitions to "Thoughts".
 */
@Composable
fun ThinkingSection(
    segment: ThinkingParser.Segment.Thinking,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val headerLabel = if (segment.isClosed) "Thoughts" else "Thinking…"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SoloSurface, RoundedCornerShape(10.dp))
            .border(1.dp, SoloBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                contentDescription = if (expanded) "Hide thoughts" else "Show thoughts",
                tint = SoloMutedForeground,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text  = headerLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = if (segment.isClosed) SoloMutedForeground else SoloAccent,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                SoloStreamingMarkdown(text = segment.text)
            }
        }
    }
}
