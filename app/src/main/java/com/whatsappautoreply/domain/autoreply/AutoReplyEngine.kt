package com.whatsappautoreply.domain.autoreply

import android.util.Log
import com.whatsappautoreply.data.database.dao.ChatDao
import com.whatsappautoreply.data.database.dao.MessageDao
import com.whatsappautoreply.data.database.dao.SettingsDao
import com.whatsappautoreply.data.database.entity.MessageDirection
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.data.remote.llm.HuggingFaceLLMClient
import com.whatsappautoreply.util.DebugLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core auto-reply decision engine
 * Determines when and whether to generate auto-replies
 */
@Singleton
class AutoReplyEngine @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val settingsDao: SettingsDao,
    private val llmClient: HuggingFaceLLMClient
) {
    companion object {
        private const val TAG = "AutoReplyEngine"
        private const val CONTEXT_WINDOW_SIZE = 15
        private const val MILLIS_PER_MINUTE = 60_000L
    }

    private val chatLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Evaluate whether to auto-reply to a new incoming message
     * Optimized for natural 24/7 conversations
     */
    /**
     * Evaluate whether to auto-reply to a new incoming message
     * Optimized for natural 24/7 conversations
     */
    suspend fun evaluateMessage(
        chatId: String,
        messageId: Long,
        config: AutoReplyConfig,
        notificationKey: String?
    ): AutoReplyDecision {
        DebugLogger.logEvent(TAG, "EVALUATE_START", mapOf(
            "chatId" to chatId,
            "messageId" to messageId,
            "globalEnabled" to config.isGloballyEnabled,
            "notificationKey" to (notificationKey ?: "null")
        ))
        
        val mutex = chatLocks.getOrPut(chatId) { Mutex() }
        
        return mutex.withLock {
            try {
            // Step 1: Check global kill switch
            if (!config.isGloballyEnabled) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "Global auto-reply is disabled",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }

            // Step 1.5: Check quiet hours
            if (isInQuietHours(config)) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "Currently in quiet hours",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }

            // Step 2: Get chat settings
            val chat = chatDao.getChatById(chatId)
            if (chat == null) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "Chat not found",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }

            // Step 3: Check per-chat auto-reply enabled
            if (!chat.autoReplyEnabled) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "Auto-reply disabled for this chat",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }

            // Step 4: Filter out group chats (privacy/spam prevention)
            if (config.excludeGroupChats && chat.isGroup) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "Auto-reply disabled for group chats",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }

            // Step 5: Get the message
            val message = messageDao.getMessageById(messageId)
            if (message == null) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "Message not found",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }

            // Step 6: Verify message is incoming
            if (message.direction != MessageDirection.INCOMING) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "Message is not incoming",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }

            // Step 6.1: Check "Reply to questions only"
            if (config.replyToQuestionsOnly) {
                val text = message.text?.trim() ?: ""
                val lowerText = text.lowercase()

                // Fast path: obvious question markers (no LLM call needed)
                val fastPathQuestion = text.endsWith("?") ||
                    lowerText.startsWith("who") || lowerText.startsWith("what") ||
                    lowerText.startsWith("where") || lowerText.startsWith("when") ||
                    lowerText.startsWith("why") || lowerText.startsWith("how") ||
                    lowerText.contains("kab ") || lowerText.contains("kaise") ||
                    lowerText.contains("ki holo") || lowerText.contains("ki hobe") ||
                    lowerText.contains("ki korcho") || lowerText.contains("kothay") ||
                    lowerText.contains("keno") || lowerText.contains("kemon") ||
                    lowerText.endsWith("na?") || lowerText.endsWith("no?") || lowerText.endsWith("huh?")

                val isQuestion = if (fastPathQuestion) {
                    DebugLogger.logEvent(TAG, "QUESTION_FAST_PATH", mapOf("chatId" to chatId))
                    true
                } else {
                    // Slow path: ask LLM (only for ambiguous messages)
                    llmClient.isQuestionOrExpectsReply(text).also {
                        DebugLogger.logEvent(TAG, "QUESTION_LLM_PATH", mapOf("chatId" to chatId, "result" to it))
                    }
                }

                if (!isQuestion) {
                    return AutoReplyDecision(
                        shouldReply = false,
                        reason = "Message is not a question (LLM+keyword check)",
                        chatId = chatId,
                        messageId = messageId,
                        notificationKey = notificationKey
                    )
                }
            }

            // Step 6.2: Check "Wait for user" (don't reply if user was active recently)
            if (config.waitForUserSeconds > 0) {
                val lastUserMessage = messageDao.getLastOutgoingMessage(chatId)
                if (lastUserMessage != null) {
                    val timeSinceUserMessage = System.currentTimeMillis() - lastUserMessage.timestamp
                    val waitMillis = config.waitForUserSeconds * 1000L
                    if (timeSinceUserMessage < waitMillis) {
                        return AutoReplyDecision(
                            shouldReply = false,
                            reason = "User active recently (${timeSinceUserMessage / 1000}s ago)",
                            chatId = chatId,
                            messageId = messageId,
                            notificationKey = notificationKey
                        )
                    }
                }
            }

            // Step 7: Optional minimal cooldown (if configured > 0)
            // This allows rapid back-and-forth like real conversations
            if (config.cooldownSeconds > 0) {
                val lastAutoReply = chat.lastLLMReplyTimestamp
                if (lastAutoReply != null) {
                    val timeSinceLastReply = System.currentTimeMillis() - lastAutoReply
                    val cooldownMillis = config.cooldownSeconds.toLong() * 1000L
                    if (timeSinceLastReply < cooldownMillis) {
                        return AutoReplyDecision(
                            shouldReply = false,
                            reason = "Cooldown active (${timeSinceLastReply / 1000}s since last reply)",
                            chatId = chatId,
                            messageId = messageId,
                            notificationKey = notificationKey
                        )
                    }
                }
            }

            // Step 7.1: Check rate limits
            if (!checkRateLimit(chatId, config)) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "Rate limit exceeded (Safety Mode)",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }
            
            // Step 7.2 & 7.3: Monologue detection and Loop prevention
            val latestMsg = messageDao.getRecentMessagesForChat(chatId, 1).firstOrNull()
            if (latestMsg != null && latestMsg.messageId != messageId) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "A newer message has arrived, skipping older one",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }
            
            if (latestMsg?.direction == MessageDirection.BOT_OUTGOING) {
                return AutoReplyDecision(
                    shouldReply = false,
                    reason = "Last message was BOT_OUTGOING (Loop prevention)",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }

            // Step 8: Build context
            val context = buildContext(chatId)

            // Step 9: Resolve tone hint (no extra LLM call — persona auto-adapts)
            // Per-chat tone setting is passed as a gentle hint, not a hard override
            val resolvedTone = when {
                !chat.preferredTone.isNullOrBlank() && chat.preferredTone != "auto" -> chat.preferredTone
                else -> "auto"
            }
            // Mood is no longer pre-analyzed — the persona prompt handles it naturally
            val inferredMood: String? = null

            // Step 10: Calculate delay with jitter
            val delayMillis = calculateDelay(
                config.minDelaySeconds,
                config.maxDelaySeconds,
                message.text?.length ?: 0
            )

            DebugLogger.logEvent(TAG, "EVALUATE_APPROVED", mapOf(
                "chatId" to chatId,
                "messageId" to messageId,
                "delayMillis" to delayMillis,
                "resolvedTone" to resolvedTone,
                "inferredMood" to (inferredMood ?: "none"),
                "contextSize" to context.size
            ))

            return AutoReplyDecision(
                shouldReply = true,
                reason = "All checks passed",
                chatId = chatId,
                messageId = messageId,
                context = context,
                suggestedTone = resolvedTone,
                suggestedMood = inferredMood,
                delayMillis = delayMillis,
                notificationKey = notificationKey
            )

            } catch (e: Exception) {
                DebugLogger.logError(TAG, "EVALUATE_ERROR", e, mapOf(
                    "chatId" to chatId,
                    "messageId" to messageId
                ))
                return@withLock AutoReplyDecision(
                    shouldReply = false,
                    reason = "Error: ${e.message}",
                    chatId = chatId,
                    messageId = messageId,
                    notificationKey = notificationKey
                )
            }
        }
    }

    /**
     * Build conversation context from recent messages
     */
    private suspend fun buildContext(chatId: String): List<ContextMessage> {
        val recentMessages = messageDao.getRecentMessagesForChat(chatId, CONTEXT_WINDOW_SIZE)
        return recentMessages.reversed().map { msg ->
            ContextMessage(
                text = msg.text,
                isIncoming = msg.direction == MessageDirection.INCOMING,
                timestamp = msg.timestamp,
                senderName = msg.senderName
            )
        }
    }

    /**
     * Check if current time is within quiet hours
     */
    private fun isInQuietHours(config: AutoReplyConfig): Boolean {
        val start = config.quietHoursStart ?: return false
        val end = config.quietHoursEnd ?: return false
        
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return if (start < end) {
            currentHour in start until end
        } else {
            // Handles overnight quiet hours (e.g., 23:00 - 07:00)
            currentHour >= start || currentHour < end
        }
    }

    /**
     * Check rate limiting (max replies per time window)
     */
    private suspend fun checkRateLimit(chatId: String, config: AutoReplyConfig): Boolean {
        if (!config.safetyModeEnabled) return true
        
        val now = System.currentTimeMillis()
        val tenMinsAgo = now - (10 * MILLIS_PER_MINUTE)
        val oneHourAgo = now - (60 * MILLIS_PER_MINUTE)
        
        // Per-chat limit (increased to 30 per 10 mins for long conversations)
        val recentAutoReplies = messageDao.getMessagesForChatSince(chatId, tenMinsAgo)
            .count { it.direction == MessageDirection.BOT_OUTGOING }
            
        if (recentAutoReplies >= config.maxRepliesPer10Min.coerceAtLeast(30)) {
            DebugLogger.logEvent(TAG, "RATE_LIMIT_EXCEEDED_CHAT", mapOf("chatId" to chatId))
            return false
        }
        
        // Global limit (increased to 200 per hour)
        val globalCount = messageDao.getGlobalAutoReplyCountSince(oneHourAgo)
        if (globalCount >= 200) {
            DebugLogger.logEvent(TAG, "RATE_LIMIT_EXCEEDED_GLOBAL", mapOf("count" to globalCount))
            return false
        }
        
        return true
    }

    /**
     * Calculate random delay with jitter
     * Reduced for faster response times while maintaining a slight natural pause
     */
    private fun calculateDelay(
        minSeconds: Int,
        maxSeconds: Int,
        messageLength: Int
    ): Long {
        val safeMin = minSeconds.coerceAtLeast(0)
        val safeMax = maxSeconds.coerceAtLeast(safeMin)
        
        val baseDelay = if (safeMin == safeMax) safeMin else (safeMin..safeMax).random()
        
        // Add minimal bonus (max 2s) for very long messages
        val lengthBonus = when {
            messageLength > 200 -> 2
            messageLength > 100 -> 1
            else -> 0
        }
        
        val totalSeconds = baseDelay + lengthBonus
        return totalSeconds * 1000L
    }
}
