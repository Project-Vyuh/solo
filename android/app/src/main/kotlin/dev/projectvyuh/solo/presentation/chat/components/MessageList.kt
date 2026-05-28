package dev.projectvyuh.solo.presentation.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.projectvyuh.solo.domain.model.Conversation
import dev.projectvyuh.solo.domain.model.Role

/**
 * Message list with smart auto-scroll: only follows the latest token if the
 * user is already at the bottom. If they scrolled up to re-read, we don't
 * yank them down when more tokens arrive.
 */
@Composable
fun MessageList(
    conversation: Conversation,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val messages = conversation.messages.filter { it.role != Role.SYSTEM }

    // Are we within ~120dp of the bottom? If so, follow new content.
    val isPinnedToBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            val totalItems = layoutInfo.totalItemsCount
            lastVisible.index >= totalItems - 1 ||
                (lastVisible.index == totalItems - 1 && lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset + 120)
        }
    }

    // Auto-scroll when:
    //  (a) a new message is appended (count grows), OR
    //  (b) the last message is streaming and we're already pinned to bottom
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (isPinnedToBottom) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            when (message.role) {
                Role.USER       -> UserMessageBubble(message)
                Role.ASSISTANT  -> AssistantMessage(message)
                Role.SYSTEM, Role.TOOL -> Unit
            }
        }
    }
}
