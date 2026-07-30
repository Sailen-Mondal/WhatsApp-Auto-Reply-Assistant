package com.whatsappautoreply.data.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.Log
import com.whatsappautoreply.data.database.dao.ChatDao
import com.whatsappautoreply.data.database.dao.MessageDao
import com.whatsappautoreply.data.database.dao.SettingsDao
import com.whatsappautoreply.data.database.entity.*
import com.whatsappautoreply.util.ChatUtils
import com.whatsappautoreply.util.DebugLogger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationProcessor @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val settingsDao: SettingsDao,
    private val notificationStore: NotificationStore
) {
    private val TAG = "NotificationProcessor"
    
    // Track the timestamp of the last processed message for each chat to prevent duplicates
    // Maps chatId -> timestamp of the newest message seen
    private val lastProcessedTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Process notification and return message ID if a new incoming message was created
     * @return Pair of (chatId, messageId) if new incoming message, null otherwise
     */
    suspend fun processNotification(sbn: StatusBarNotification): Pair<String, Long>? {
        val notification = sbn.notification
        val extras = notification.extras ?: run {
            DebugLogger.logWarning(TAG, "SKIP_NO_EXTRAS", mapOf("sbn_id" to sbn.id))
            return null
        }

        // Extract title (usually contact/group name)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: run {
            DebugLogger.logWarning(TAG, "SKIP_NO_TITLE", mapOf("sbn_id" to sbn.id))
            return null
        }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // Generate chatId from title
        val chatId = ChatUtils.generateChatId(title)

        DebugLogger.logEvent(TAG, "PROCESS_START", mapOf(
            "title" to title,
            "text" to text.take(80),
            "chatId" to chatId,
            "sbn_id" to sbn.id,
            "sbn_key" to sbn.key,
            "post_time" to sbn.postTime
        ))

        // Store notification for reply action access
        notificationStore.storeNotification(chatId, sbn)

        // Determine if it's a group (heuristic: if title contains multiple names or specific patterns)
        val isGroup = isLikelyGroup(title, text)

        // Get or create chat
        var chat = chatDao.getChatById(chatId)
        val isNewChat = chat == null
        if (chat == null) {
            val globalAutoReply = settingsDao.getSetting("auto_reply_enabled")?.value?.toBoolean() ?: false
            chat = ChatEntity(
                chatId = chatId,
                title = title,
                isGroup = isGroup,
                autoReplyEnabled = globalAutoReply,
                lastMessageTimestamp = sbn.postTime
            )
            chatDao.insertChat(chat)
        } else {
            // Update last message timestamp
            chatDao.updateLastMessageTimestamp(chatId, sbn.postTime)
        }

        DebugLogger.logEvent(TAG, "CHAT_RESOLVED", mapOf(
            "chatId" to chatId,
            "isNewChat" to isNewChat,
            "isGroup" to isGroup
        ))

        // Parse message details and track if incoming message
        var incomingMessageId: Long? = null
        val messagingStyle = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        
        if (messagingStyle != null) {
            val messageCount = messagingStyle.messages.size
            DebugLogger.logEvent(TAG, "PROCESSING_PATH", mapOf(
                "path" to "MessagingStyle",
                "message_count_in_style" to messageCount
            ))
            // Handle MessagingStyle (more detailed info)
            incomingMessageId = processMessagingStyle(chatId, messagingStyle, sbn)
        } else {
            DebugLogger.logEvent(TAG, "PROCESSING_PATH", mapOf(
                "path" to "BasicNotification"
            ))
            // Fallback to basic notification parsing
            incomingMessageId = processBasicNotification(chatId, title, text, sbn)
        }

        DebugLogger.logEvent(TAG, "PROCESS_RESULT", mapOf(
            "chatId" to chatId,
            "incomingMessageId" to (incomingMessageId ?: "null"),
            "returning_pair" to (incomingMessageId != null)
        ))

        return if (incomingMessageId != null) {
            Pair(chatId, incomingMessageId)
        } else {
            null
        }
    }

    /**
     * @return message ID of the last INCOMING message, or null
     */
    private suspend fun processMessagingStyle(
        chatId: String,
        messagingStyle: androidx.core.app.NotificationCompat.MessagingStyle,
        sbn: StatusBarNotification
    ): Long? {
        val conversationTitle = messagingStyle.conversationTitle?.toString() ?: ""
        val messages = messagingStyle.messages
        var lastIncomingId: Long? = null

        DebugLogger.logEvent(TAG, "MESSAGING_STYLE_START", mapOf(
            "chatId" to chatId,
            "conversationTitle" to conversationTitle,
            "total_messages" to messages.size
        ))

        if (messages.isNotEmpty()) {
            // Process each message in the conversation
            messages.forEachIndexed { index, message ->
                val senderName = message.sender?.toString()
                val messageText = message.text?.toString() ?: ""
                val timestamp = message.timestamp

                val direction = determineDirection(senderName, messageText)
                val mediaType = detectMediaType(messageText)

                // Check if we've already processed this message based on timestamp
                val lastProcessed = lastProcessedTimestamps[chatId] ?: 0L
                if (timestamp <= lastProcessed) {
                    DebugLogger.logEvent(TAG, "SKIP_ALREADY_PROCESSED", mapOf(
                        "chatId" to chatId,
                        "timestamp" to timestamp,
                        "lastProcessed" to lastProcessed,
                        "text_preview" to messageText.take(20)
                    ))
                    return@forEachIndexed // Skip this message
                }

                val messageEntity = MessageEntity(
                    chatId = chatId,
                    senderName = senderName,
                    direction = direction,
                    text = messageText,
                    mediaType = mediaType,
                    timestamp = timestamp,
                    source = MessageSource.NOTIFICATION,
                    rawNotificationId = sbn.id
                )

                val messageId = messageDao.insertMessage(messageEntity)

                DebugLogger.logEvent(TAG, "MSG_INSERT_ATTEMPT", mapOf(
                    "index" to index,
                    "chatId" to chatId,
                    "sender" to (senderName ?: "null"),
                    "text" to messageText.take(50),
                    "timestamp" to timestamp,
                    "direction" to direction.name,
                    "db_returned_id" to messageId,
                    "was_duplicate" to (messageId == 0L),
                    "is_incoming" to (direction == MessageDirection.INCOMING)
                ))

                if (direction == MessageDirection.INCOMING) {
                    if (messageId > 0L) {
                        // Genuinely new incoming message
                        lastIncomingId = messageId
                    } else {
                        DebugLogger.logEvent(TAG, "DUPLICATE_INCOMING_IGNORED", mapOf(
                            "chatId" to chatId,
                            "text" to messageText.take(50),
                            "timestamp" to timestamp
                        ))
                    }
                }
            }
        }   // end if (messages.isNotEmpty())

        // LOOP PREVENTION: if the newest message in the style is OUTGOING, it means
        // WhatsApp just updated the notification after we sent a reply. All previous
        // INCOMING messages were already handled in the prior notification cycle.
        // Returning an incoming ID here would cause a second auto-reply to fire.
        val newestMessage = messages.maxByOrNull { it.timestamp }
        if (newestMessage != null) {
            val newestSender = newestMessage.sender?.toString()
            val newestDirection = determineDirection(newestSender, newestMessage.text?.toString() ?: "")
            if (newestDirection == MessageDirection.OUTGOING) {
                DebugLogger.logEvent(TAG, "SKIP_OUTGOING_IS_NEWEST", mapOf(
                    "chatId" to chatId,
                    "newestText" to (newestMessage.text?.toString()?.take(40) ?: ""),
                    "reason" to "bot_sent_reply_notification"
                ))
                // Still update the timestamp so we don't reprocess older messages next time
                val newestTs = messages.maxOfOrNull { it.timestamp } ?: 0L
                val currentLast = lastProcessedTimestamps[chatId] ?: 0L
                if (newestTs > currentLast) lastProcessedTimestamps[chatId] = newestTs
                return null
            }
        }

        // Update the last processed timestamp so older messages are not re-processed next time
        val newestTs = messages.maxOfOrNull { it.timestamp } ?: 0L
        val currentLast = lastProcessedTimestamps[chatId] ?: 0L
        if (newestTs > currentLast) lastProcessedTimestamps[chatId] = newestTs

        DebugLogger.logEvent(TAG, "MESSAGING_STYLE_RESULT", mapOf(
            "chatId" to chatId,
            "lastIncomingId" to (lastIncomingId ?: "null"),
            "total_processed" to messages.size
        ))

        return lastIncomingId
    }

    /**
     * @return message ID if INCOMING, null otherwise
     */
    private suspend fun processBasicNotification(
        chatId: String,
        title: String,
        text: String,
        sbn: StatusBarNotification
    ): Long? {
        // Try to extract sender name and message
        val (parsedSender, messageText) = parseNotificationText(title, text)
        
        val senderName = parsedSender ?: title
        
        val direction = determineDirection(senderName, messageText)
        val mediaType = detectMediaType(messageText)

        val messageEntity = MessageEntity(
            chatId = chatId,
            senderName = senderName,
            direction = direction,
            text = messageText,
            mediaType = mediaType,
            timestamp = sbn.postTime,
            source = MessageSource.NOTIFICATION,
            rawNotificationId = sbn.id
        )

        val messageId = messageDao.insertMessage(messageEntity)

        DebugLogger.logEvent(TAG, "BASIC_NOTIF_INSERT", mapOf(
            "chatId" to chatId,
            "parsedSender" to (parsedSender ?: "null"),
            "senderName" to senderName,
            "text" to messageText.take(50),
            "direction" to direction.name,
            "db_returned_id" to messageId,
            "was_duplicate" to (messageId == 0L)
        ))

        return if (direction == MessageDirection.INCOMING && messageId > 0L) messageId else null
    }

    private fun parseNotificationText(title: String, text: String): Pair<String?, String> {
        // Try to detect if text contains sender info
        // Format might be "Sender: Message" or "You: Message"
        val colonIndex = text.indexOf(':')
        return if (colonIndex > 0 && colonIndex < text.length - 1) {
            val potentialSender = text.substring(0, colonIndex).trim()
            val message = text.substring(colonIndex + 1).trim()
            if (potentialSender.equals("You", ignoreCase = true)) {
                Pair("You", message) // Outgoing message
            } else {
                Pair(potentialSender, message)
            }
        } else {
            Pair(null, text)
        }
    }

    private fun determineDirection(senderName: String?, messageText: String): MessageDirection {
        // Heuristics to determine direction
        if (senderName == null || senderName.equals("You", ignoreCase = true)) {
            return MessageDirection.OUTGOING
        }
        if (messageText.contains("You:", ignoreCase = true)) {
            return MessageDirection.OUTGOING
        }
        return MessageDirection.INCOMING
    }

    private fun detectMediaType(text: String): MediaType {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("photo") || lowerText.contains("image") -> MediaType.IMAGE
            lowerText.contains("video") -> MediaType.VIDEO
            lowerText.contains("voice") || lowerText.contains("audio") -> MediaType.AUDIO
            lowerText.contains("sticker") -> MediaType.STICKER
            lowerText.contains("file") || lowerText.contains("document") -> MediaType.FILE
            else -> MediaType.TEXT
        }
    }

    private fun isLikelyGroup(title: String, text: String): Boolean {
        // Heuristic: groups often have longer titles or specific patterns
        // This is a simple heuristic - can be improved
        return title.contains(",") || title.length > 30
    }

}
