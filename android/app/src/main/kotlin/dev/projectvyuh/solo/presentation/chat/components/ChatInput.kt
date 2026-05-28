package dev.projectvyuh.solo.presentation.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.presentation.theme.SoloAccent
import dev.projectvyuh.solo.presentation.theme.SoloAccentForeground
import dev.projectvyuh.solo.presentation.theme.SoloBorder
import dev.projectvyuh.solo.presentation.theme.SoloError
import dev.projectvyuh.solo.presentation.theme.SoloMutedForeground
import dev.projectvyuh.solo.presentation.theme.SoloSurface

@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean,
    isModelReady: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .weight(1f)
                .background(SoloSurface, RoundedCornerShape(20.dp))
                .border(1.dp, SoloBorder, RoundedCornerShape(20.dp))
                .heightIn(min = 44.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = if (isModelReady) "Message Solo..." else "Loading model...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SoloMutedForeground,
                )
            }
            BasicTextField(
                value         = value,
                onValueChange = onValueChange,
                enabled       = isModelReady && !isGenerating,
                textStyle     = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush   = SolidColor(SoloAccent),
                modifier      = androidx.compose.ui.Modifier.fillMaxWidth(),
                maxLines      = 6,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Send,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { onSend() },
                ),
            )
        }

        if (isGenerating) {
            IconButton(
                onClick  = onStop,
                modifier = androidx.compose.ui.Modifier.size(44.dp),
                colors   = IconButtonDefaults.iconButtonColors(
                    containerColor = SoloError,
                    contentColor   = SoloAccentForeground,
                ),
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = "Stop")
            }
        } else {
            val canSend = isModelReady && value.isNotBlank()
            IconButton(
                onClick  = onSend,
                enabled  = canSend,
                modifier = androidx.compose.ui.Modifier.size(44.dp),
                colors   = IconButtonDefaults.iconButtonColors(
                    containerColor = if (canSend) SoloAccent else SoloBorder,
                    contentColor   = SoloAccentForeground,
                    disabledContainerColor = SoloBorder,
                    disabledContentColor   = SoloMutedForeground,
                ),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}
