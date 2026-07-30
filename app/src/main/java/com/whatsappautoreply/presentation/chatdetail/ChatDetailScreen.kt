package com.whatsappautoreply.presentation.chatdetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.whatsappautoreply.data.database.entity.MessageDirection
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.data.database.entity.MediaType
import com.whatsappautoreply.data.database.entity.UserFeedback
import com.whatsappautoreply.util.DebugLogger
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatId: String,
    onBackClick: () -> Unit,
    viewModel: ChatDetailViewModel = hiltViewModel()
) {
    val chat by viewModel.getChat(chatId).collectAsStateWithLifecycle(initialValue = null)
    val messages by viewModel.getMessages(chatId).collectAsStateWithLifecycle(initialValue = emptyList())
    val suggestedReply by viewModel.suggestedReply.collectAsStateWithLifecycle()
    val editedReply by viewModel.editedReply.collectAsStateWithLifecycle()
    val suggestedTone by viewModel.suggestedTone.collectAsStateWithLifecycle()
    val suggestedMood by viewModel.suggestedMood.collectAsStateWithLifecycle()
    val isSuggesting by viewModel.isSuggesting.collectAsStateWithLifecycle()
    val suggestionError by viewModel.suggestionError.collectAsStateWithLifecycle()
    val userFeedback by viewModel.userFeedback.collectAsStateWithLifecycle()
    val copySuccessMessage by viewModel.copySuccessMessage.collectAsStateWithLifecycle()
    val isSendingReply by viewModel.isSendingReply.collectAsStateWithLifecycle()
    val replySentStatus by viewModel.replySentStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDebugLogs by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId)
    }

    LaunchedEffect(copySuccessMessage) {
        copySuccessMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearCopyMessage()
            }
        }
    }

    LaunchedEffect(replySentStatus) {
        replySentStatus?.let { success ->
            scope.launch {
                val message = if (success) "Reply sent successfully!" else "Failed to send reply. Is the notification still active?"
                snackbarHostState.showSnackbar(message)
                viewModel.clearReplyStatus()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = chat?.title ?: "Chat",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (chat != null) {
                            Text(
                                text = if (chat!!.isGroup) "Group" else "Individual",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Debug log viewer button
                    IconButton(onClick = { showDebugLogs = true }) {
                        Icon(
                            Icons.Default.BugReport,
                            contentDescription = "Debug Logs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (chat != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Auto-Reply",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Switch(
                                checked = chat!!.autoReplyEnabled,
                                onCheckedChange = { viewModel.toggleAutoReply(chatId, it) },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Auto-scroll to bottom on new messages
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.messageId }) { message ->
                    MessageBubble(message = message)
                }
            }

            // Mood banner
            AnimatedVisibility(
                visible = !suggestedMood.isNullOrBlank(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Mood,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Vibe: ${suggestedMood ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            HorizontalDivider()

            SuggestionSection(
                isSuggesting = isSuggesting,
                suggestedReply = suggestedReply,
                editedReply = editedReply,
                suggestedTone = suggestedTone,
                error = suggestionError,
                userFeedback = userFeedback,
                isSendingReply = isSendingReply,
                onSuggestClick = { viewModel.requestSuggestedReply(chatId) },
                onCopyClick = { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Suggested Reply", text)
                    clipboard.setPrimaryClip(clip)
                    viewModel.onCopySuccess()
                },
                onEditChange = { viewModel.updateEditedReply(it) },
                onFeedbackClick = { feedback ->
                    viewModel.submitFeedback(feedback)
                },
                onSendClick = { text ->
                    viewModel.sendReply(chatId, text)
                }
            )
        }
    }

    // Debug Log Dialog
    if (showDebugLogs) {
        DebugLogDialog(
            onDismiss = { showDebugLogs = false }
        )
    }
}

@Composable
fun MessageBubble(message: MessageEntity) {
    val isIncoming = message.direction == MessageDirection.INCOMING
    val alignment = if (isIncoming) Alignment.Start else Alignment.End
    val bubbleShape = remember(isIncoming) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isIncoming) 4.dp else 16.dp,
            bottomEnd = if (isIncoming) 16.dp else 4.dp
        )
    }
    val timeText = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(
                    if (isIncoming)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (message.senderName != null && isIncoming) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                when (message.mediaType) {
                    MediaType.TEXT -> {
                        Text(
                            text = message.text ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isIncoming)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    else -> {
                        Text(
                            text = "[${message.mediaType.name}] ${message.text ?: message.mediaCaption ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isIncoming)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                if (message.direction == MessageDirection.BOT_OUTGOING) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "🤖 Auto",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp)
        )
    }
}

@Composable
private fun SuggestionSection(
    isSuggesting: Boolean,
    suggestedReply: String?,
    editedReply: String?,
    suggestedTone: String?,
    error: String?,
    userFeedback: UserFeedback?,
    isSendingReply: Boolean,
    onSuggestClick: () -> Unit,
    onCopyClick: (String) -> Unit,
    onEditChange: (String) -> Unit,
    onFeedbackClick: (UserFeedback) -> Unit,
    onSendClick: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    val displayText = editedReply ?: suggestedReply

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI suggestion",
                style = MaterialTheme.typography.titleSmall
            )
            Button(
                onClick = onSuggestClick,
                enabled = !isSuggesting
            ) {
                if (isSuggesting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(16.dp)
                            .widthIn(max = 16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(text = if (isSuggesting) "Generating..." else "Suggest reply")
            }
        }

        if (!suggestedTone.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Suggested Tone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = suggestedTone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        when {
            !error.isNullOrBlank() -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            !displayText.isNullOrBlank() -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = displayText,
                            onValueChange = onEditChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Edit reply") },
                            maxLines = 4,
                            trailingIcon = {
                                TextButton(onClick = { isEditing = false }) {
                                    Text("Done")
                                }
                            }
                        )
                    } else {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                                    text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Copy button
                            IconButton(
                                onClick = { onCopyClick(displayText) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Edit button
                            TextButton(
                                onClick = { isEditing = !isEditing }
                            ) {
                                Text(if (isEditing) "Cancel" else "Edit")
                            }
                        }

                        // Send button
                        Button(
                            onClick = { onSendClick(displayText) },
                            enabled = !isSendingReply,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            if (isSendingReply) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Text(if (isSendingReply) "Sending..." else "Send")
                        }

                        // Feedback buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { onFeedbackClick(UserFeedback.UPVOTE) },
                                modifier = Modifier.size(40.dp),
                                enabled = userFeedback != UserFeedback.UPVOTE
                            ) {
                                Icon(
                                    Icons.Default.ThumbUp,
                                    contentDescription = "Upvote",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (userFeedback == UserFeedback.UPVOTE)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onFeedbackClick(UserFeedback.DOWNVOTE) },
                                modifier = Modifier.size(40.dp),
                                enabled = userFeedback != UserFeedback.DOWNVOTE
                            ) {
                                Icon(
                                    Icons.Default.ThumbDown,
                                    contentDescription = "Downvote",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (userFeedback == UserFeedback.DOWNVOTE)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                Text(
                    text = "Tap \"Suggest reply\" to generate a response for this chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DebugLogDialog(onDismiss: () -> Unit) {
    var logs by remember { mutableStateOf(DebugLogger.getRecentLogs(500)) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var copyConfirmation by remember { mutableStateOf(false) }

    // Auto-scroll to bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    // Reset copy confirmation after 2s
    LaunchedEffect(copyConfirmation) {
        if (copyConfirmation) {
            kotlinx.coroutines.delay(2000)
            copyConfirmation = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Debug Logs (${logs.size})")
                    Row {
                        TextButton(onClick = {
                            logs = DebugLogger.getRecentLogs(500)
                        }) {
                            Text("Refresh")
                        }
                        TextButton(onClick = {
                            DebugLogger.clearLogs()
                            logs = emptyList()
                        }) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                // Copy All button — prominent, full width
                Button(
                    onClick = {
                        val allText = logs.joinToString("\n")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Debug Logs", allText)
                        clipboard.setPrimaryClip(clip)
                        copyConfirmation = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    enabled = logs.isNotEmpty()
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text(if (copyConfirmation) "✅ Copied to clipboard!" else "📋 Copy All Logs")
                }
            }
        },
        text = {
            if (logs.isEmpty()) {
                Text(
                    text = "No debug logs yet. Logs appear when notifications arrive or replies are sent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                    ) {
                        items(logs) { entry ->
                            val color = when {
                                entry.contains("ERROR/") -> MaterialTheme.colorScheme.error
                                entry.contains("WARN/") -> MaterialTheme.colorScheme.tertiary
                                entry.contains("SEND_SUCCESS") || entry.contains("WORKER_COMPLETE_SUCCESS") ->
                                    MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = entry,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                ),
                                color = color,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                    // Show log file path for reference
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Log file: ${DebugLogger.getLogFilePath()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
