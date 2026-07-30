package com.whatsappautoreply.presentation.chatlist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whatsappautoreply.R
import com.whatsappautoreply.data.database.entity.ChatEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentFilter by viewModel.filter.collectAsStateWithLifecycle()

    val activeCount = remember(chats) { chats.count { it.autoReplyEnabled } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo_small),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Auto Reply",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                "$activeCount active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onAnalyticsClick) {
                        Icon(Icons.Default.Analytics, contentDescription = "Analytics")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentFilter == ChatFilter.ALL,
                    onClick = { viewModel.setFilter(ChatFilter.ALL) },
                    label = { Text("All (${chats.size})") }
                )
                FilterChip(
                    selected = currentFilter == ChatFilter.AUTO_REPLY_ENABLED,
                    onClick = { viewModel.setFilter(ChatFilter.AUTO_REPLY_ENABLED) },
                    label = { Text("Active") }
                )
                FilterChip(
                    selected = currentFilter == ChatFilter.AUTO_REPLY_DISABLED,
                    onClick = { viewModel.setFilter(ChatFilter.AUTO_REPLY_DISABLED) },
                    label = { Text("Paused") }
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (chats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = if (currentFilter == ChatFilter.ALL)
                                "No chats yet.\nSend yourself a message on WhatsApp to get started."
                            else
                                "No chats matching this filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(chats, key = { it.chatId }) { chat ->
                        ChatListItem(
                            chat = chat,
                            onClick = { onChatClick(chat.chatId) },
                            onToggleAutoReply = { enabled ->
                                viewModel.toggleAutoReply(chat.chatId, enabled)
                            }
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    chat: ChatEntity,
    onClick: () -> Unit,
    onToggleAutoReply: (Boolean) -> Unit
) {
    val timestamp = chat.lastMessageTimestamp
    val timeText = remember(timestamp) {
        val now = System.currentTimeMillis()
        when {
            now - timestamp < 86_400_000L -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            now - timestamp < 604_800_000L -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
            else -> SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    val avatarBgColor = remember(chat.title) { avatarColor(chat.title) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with auto-reply indicator
            Box(modifier = Modifier.size(52.dp)) {
                // Initials avatar
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(avatarBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chat.title.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                }
                // Active indicator dot
                if (chat.autoReplyEnabled) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name + last message preview
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (chat.isGroup) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Group",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (!chat.preferredTone.isNullOrBlank() && chat.preferredTone != "auto") {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = chat.preferredTone.replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (chat.autoReplyEnabled) "Auto-reply enabled" else "Auto-reply paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chat.autoReplyEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right side: time + toggle
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = chat.autoReplyEnabled,
                onCheckedChange = onToggleAutoReply,
                modifier = Modifier.height(24.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/** Generate a stable color per contact name */
private fun avatarColor(name: String): Color {
    val colors = listOf(
        Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFD32F2F), Color(0xFF7B1FA2),
        Color(0xFF0097A7), Color(0xFF00796B), Color(0xFFE64A19), Color(0xFF5D4037),
        Color(0xFF455A64), Color(0xFF6A1B9A)
    )
    return colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
}

