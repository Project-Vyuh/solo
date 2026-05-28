package dev.projectvyuh.solo.presentation.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloAccentForeground
import dev.projectvyuh.solo.presentation.theme.SoloBorder
import dev.projectvyuh.solo.presentation.theme.SoloError
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground

/**
 * Mic button next to the text input. Three visual states:
 *  - [VoiceButtonState.Idle]: outlined mic; tap to start a voice turn
 *  - [VoiceButtonState.Active]: filled red stop with subtle pulse; tap to abort
 *  - [VoiceButtonState.Disabled]: muted mic-off; tap is no-op (permission/model missing)
 */
@Composable
fun VoiceButton(
    state: VoiceButtonState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state != VoiceButtonState.Disabled
    val container = when (state) {
        VoiceButtonState.Active   -> SoloError
        VoiceButtonState.Idle     -> SoloBorder
        VoiceButtonState.Disabled -> SoloBorder
    }
    val content = when (state) {
        VoiceButtonState.Active   -> SoloAccentForeground
        VoiceButtonState.Idle     -> SoloAccent
        VoiceButtonState.Disabled -> SoloMutedForeground
    }
    val icon = when (state) {
        VoiceButtonState.Active   -> Icons.Rounded.Stop
        VoiceButtonState.Idle     -> Icons.Rounded.Mic
        VoiceButtonState.Disabled -> Icons.Rounded.MicOff
    }

    val pulseScale = if (state == VoiceButtonState.Active) {
        val transition = rememberInfiniteTransition(label = "mic-pulse")
        val v by transition.animateFloat(
            initialValue = 1.0f,
            targetValue  = 1.08f,
            animationSpec = infiniteRepeatable(
                animation  = tween(durationMillis = 700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mic-pulse-v",
        )
        v
    } else 1.0f

    IconButton(
        onClick  = onTap,
        enabled  = enabled,
        modifier = modifier.size(44.dp).scale(pulseScale),
        colors   = IconButtonDefaults.iconButtonColors(
            containerColor = container,
            contentColor   = content,
            disabledContainerColor = SoloBorder,
            disabledContentColor   = SoloMutedForeground,
        ),
    ) {
        Icon(icon, contentDescription = when (state) {
            VoiceButtonState.Active   -> "Stop listening"
            VoiceButtonState.Idle     -> "Speak to Solo"
            VoiceButtonState.Disabled -> "Voice unavailable"
        })
    }
}

enum class VoiceButtonState { Idle, Active, Disabled }
