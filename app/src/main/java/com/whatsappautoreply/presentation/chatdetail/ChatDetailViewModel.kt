package com.whatsappautoreply.presentation.chatdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsappautoreply.data.database.entity.ChatEntity
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.data.database.entity.UserFeedback
import com.whatsappautoreply.data.remote.llm.HuggingFaceLLMClient
import com.whatsappautoreply.data.notification.NotificationReplySender
import com.whatsappautoreply.data.repository.ChatRepository
import com.whatsappautoreply.data.repository.LLMLogRepository
import com.whatsappautoreply.data.repository.MessageRepository
import com.whatsappautoreply.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val llmClient: HuggingFaceLLMClient,
    private val llmLogRepository: LLMLogRepository,
    private val notificationReplySender: NotificationReplySender
) : ViewModel() {

    fun getChat(chatId: String): Flow<ChatEntity?> = chatRepository.getChatById(chatId)

    fun getMessages(chatId: String): StateFlow<List<MessageEntity>> =
        messageRepository.getMessagesByChatId(chatId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val suggestedReply: MutableStateFlow<String?> = MutableStateFlow(null)
    val editedReply: MutableStateFlow<String?> = MutableStateFlow(null)
    val isSuggesting: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val suggestionError: MutableStateFlow<String?> = MutableStateFlow(null)
    val currentLogId: MutableStateFlow<Long?> = MutableStateFlow(null)
    val userFeedback: MutableStateFlow<UserFeedback?> = MutableStateFlow(null)
    val suggestedTone: MutableStateFlow<String?> = MutableStateFlow(null)
    val suggestedMood: MutableStateFlow<String?> = MutableStateFlow(null)
    val copySuccessMessage: MutableStateFlow<String?> = MutableStateFlow(null)
    val isSendingReply: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val replySentStatus: MutableStateFlow<Boolean?> = MutableStateFlow(null)

    fun loadChat(chatId: String) {
        viewModelScope.launch {
            val recent = messageRepository.getRecentMessages(chatId, limit = 15)
            if (recent.isNotEmpty()) {
                // analyzeToneAndMood now returns (emotion, dynamic) from the new emotional analysis
                val (emotion, dynamic) = llmClient.analyzeToneAndMood(recent)
                if (!emotion.isNullOrBlank()) {
                    // Build a richer display string for the mood banner
                    val vibeDisplay = buildString {
                        append(emotion.replaceFirstChar { it.uppercaseChar() })
                        if (!dynamic.isNullOrBlank()) {
                            append(" · ${dynamic.replace("_", " ")}")
                        }
                    }
                    suggestedMood.value = vibeDisplay
                }
                // Tone hint comes from per-chat setting, not LLM analysis
            }
        }
    }

    fun requestSuggestedReply(chatId: String) {
        viewModelScope.launch {
            isSuggesting.value = true
            suggestionError.value = null
            suggestedReply.value = null
            editedReply.value = null
            currentLogId.value = null
            userFeedback.value = null
            copySuccessMessage.value = null

            val recent = messageRepository.getRecentMessages(chatId, limit = 15)
            if (recent.isEmpty()) {
                isSuggesting.value = false
                suggestionError.value = "No context available yet."
                return@launch
            }

            val reply = llmClient.generateReply(
                chatId = chatId,
                recentMessages = recent,
                preferredTone = suggestedTone.value // Use detected tone
            )

            isSuggesting.value = false
            if (reply.isNullOrBlank()) {
                suggestionError.value = "Could not generate reply. Check API key or try again."
            } else {
                suggestedReply.value = reply
                editedReply.value = reply
                // Get the latest log to track its ID for feedback
                val latestLog = llmLogRepository.getLatestLogForChat(chatId)
                currentLogId.value = latestLog?.logId
            }
        }
    }

    fun updateEditedReply(text: String) {
        editedReply.value = text
    }

    fun submitFeedback(feedback: UserFeedback) {
        val logId = currentLogId.value
        if (logId != null && logId > 0) {
            viewModelScope.launch {
                llmLogRepository.updateFeedback(logId, feedback)
                userFeedback.value = feedback
            }
        }
    }

    fun toggleAutoReply(chatId: String, enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.updateAutoReplyEnabled(chatId, enabled)
        }
    }

    fun applyTone(chatId: String, tone: String) {
        viewModelScope.launch {
            chatRepository.updatePreferredTone(chatId, tone)
            suggestedTone.value = tone
        }
    }

    fun onCopySuccess() {
        copySuccessMessage.value = "Copied to clipboard!"
    }

    fun clearCopyMessage() {
        copySuccessMessage.value = null
    }

    fun sendReply(chatId: String, text: String) {
        viewModelScope.launch {
            isSendingReply.value = true
            replySentStatus.value = null
            
            val success = notificationReplySender.sendReply(chatId, text, null)
            
            isSendingReply.value = false
            replySentStatus.value = success
            
            if (success) {
                // Refresh messages to show the new outgoing message
                // (Though it might take a moment to appear in notifications)
            }
        }
    }

    fun clearReplyStatus() {
        replySentStatus.value = null
    }
}

