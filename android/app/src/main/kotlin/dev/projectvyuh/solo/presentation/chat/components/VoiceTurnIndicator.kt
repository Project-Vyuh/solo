package dev.projectvyuh.solo.presentation.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.data.voice.orchestration.VoiceTurnState
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloAccentMuted
import dev.projectvyuh.solo.presentation.theme.SoloError
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground
import dev.projectvyuh.solo.presentation.theme.SoloSuccess
import dev.projectvyuh.solo.presentation.theme.SoloSurface

/**
 * Pill near the top of the chat that surfaces the voice turn state.
 * Renders only when the orchestrator is non-Idle.
 */
@Composable
fun VoiceTurnIndicator(
    state: VoiceTurnState,
    modifier: Modifier = Modifier,
) {
    val visible = state !is VoiceTurnState.Idle
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(),
        exit    = fadeOut(),
        modifier = modifier,
    ) {
        val (label, dotColor) = labelAndDot(state)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SoloSurface)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PulsingDot(dotColor)
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "voice-dot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue  = 0.3f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-dot-alpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

private fun labelAndDot(state: VoiceTurnState): Pair<String, Color> = when (state) {
    is VoiceTurnState.Listening -> "Listening"   to SoloAccent
    is VoiceTurnState.Thinking  -> "Thinking"    to SoloAccentMuted
    is VoiceTurnState.Speaking  -> "Speaking"    to SoloSuccess
    is VoiceTurnState.Failed    -> "Voice error" to SoloError
    VoiceTurnState.Idle         -> ""            to Color.Transparent
}
