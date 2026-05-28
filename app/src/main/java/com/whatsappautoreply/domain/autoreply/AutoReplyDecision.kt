package com.whatsappautoreply.domain.autoreply

/**
 * Decision result from AutoReplyEngine about whether to auto-reply
 */
data class AutoReplyDecision(
    val shouldReply: Boolean,
    val reason: String,
    val chatId: String,
    val messageId: Long,
    val context: List<ContextMessage> = emptyList(),
    val suggestedTone: String? = null,
    val suggestedMood: String? = null,
    val delayMillis: Long = 0L,
    val notificationKey: String? = null
)

/**
 * Simplified message for context building
 */
data class ContextMessage(
    val text: String?,
    val isIncoming: Boolean,
    val timestamp: Long,
    val senderName: String? = null
)
