package com.whatsappautoreply.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.whatsappautoreply.data.database.dao.ChatDao
import com.whatsappautoreply.data.database.dao.LLMLogDao
import com.whatsappautoreply.data.database.dao.MessageDao
import com.whatsappautoreply.data.database.dao.SettingsDao
import com.whatsappautoreply.data.database.entity.LLMLogEntity
import com.whatsappautoreply.data.database.entity.MessageDirection
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.data.database.entity.MessageSource
import com.whatsappautoreply.data.database.entity.UserFeedback
import com.whatsappautoreply.data.notification.NotificationReplySender
import com.whatsappautoreply.data.remote.llm.HuggingFaceLLMClient
import com.whatsappautoreply.domain.autoreply.AutoReplyConfig
import com.whatsappautoreply.domain.autoreply.ContextMessage
import com.whatsappautoreply.util.DebugLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * WorkManager worker that executes delayed auto-replies
 * This ensures reliability across process deaths
 */
@HiltWorker
class AutoReplyWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val settingsDao: SettingsDao,
    private val llmLogDao: LLMLogDao,
    private val llmClient: HuggingFaceLLMClient,
    private val notificationReplySender: NotificationReplySender
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AutoReplyWorker"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_MESSAGE_ID = "message_id"
        const val KEY_TONE = "tone"
        const val KEY_MOOD = "mood"
        const val KEY_CONTEXT_JSON = "context_json"
        const val KEY_RESULT_SUCCESS = "result_success"
        const val KEY_RESULT_MESSAGE = "result_message"
        const val KEY_NOTIFICATION_KEY = "notification_key"
    }

    override suspend fun doWork(): Result {
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure(
            workDataOf(KEY_RESULT_MESSAGE to "Missing chat ID")
        )
        val messageId = inputData.getLong(KEY_MESSAGE_ID, -1L)
        if (messageId == -1L) {
            return Result.failure(
                workDataOf(KEY_RESULT_MESSAGE to "Missing message ID")
            )
        }

        val notificationKey = inputData.getString(KEY_NOTIFICATION_KEY)
        val tone = inputData.getString(KEY_TONE) ?: "auto"
        val mood = inputData.getString(KEY_MOOD)

        DebugLogger.logEvent(TAG, "WORKER_START", mapOf(
            "chatId" to chatId,
            "messageId" to messageId,
            "tone" to tone,
            "notificationKey" to (notificationKey ?: "null"),
            "run_attempt" to runAttemptCount
        ))

        try {
            // Re-validate conditions before sending
            if (!validateConditions(chatId)) {
                DebugLogger.logEvent(TAG, "CONDITIONS_INVALID", mapOf(
                    "chatId" to chatId,
                    "reason" to "Auto-reply disabled after scheduling"
                ))
                return Result.success(
                    workDataOf(
                        KEY_RESULT_SUCCESS to false,
                        KEY_RESULT_MESSAGE to "Conditions changed, auto-reply cancelled"
                    )
                )
            }

            DebugLogger.logEvent(TAG, "CONDITIONS_VALID", mapOf("chatId" to chatId))

            // Get context and tone
            val context = buildContext(chatId)
            DebugLogger.logEvent(TAG, "CONTEXT_BUILT", mapOf(
                "chatId" to chatId,
                "context_size" to context.size,
                "last_msg" to (context.lastOrNull()?.text?.take(40) ?: "none")
            ))

            // Generate reply using LLM
            DebugLogger.logEvent(TAG, "LLM_GENERATE_START", mapOf(
                "chatId" to chatId,
                "tone" to tone
            ))

            val reply = llmClient.generateReply(context, tone, mood)

            if (reply == null) {
                DebugLogger.logWarning(TAG, "LLM_NO_REPLY", mapOf(
                    "chatId" to chatId,
                    "reason" to "LLM returned null"
                ))
                return Result.success(
                    workDataOf(
                        KEY_RESULT_SUCCESS to false,
                        KEY_RESULT_MESSAGE to "LLM skipped reply"
                    )
                )
            }

            DebugLogger.logEvent(TAG, "LLM_REPLY_GENERATED", mapOf(
                "chatId" to chatId,
                "reply_length" to reply.length,
                "reply_preview" to reply.take(60)
            ))

            // Send via notification
            DebugLogger.logEvent(TAG, "SEND_VIA_NOTIFICATION_START", mapOf(
                "chatId" to chatId,
                "notificationKey" to (notificationKey ?: "null")
            ))

            val sent = notificationReplySender.sendReply(chatId, reply, notificationKey)

            if (!sent) {
                DebugLogger.logError(TAG, "SEND_FAILED", data = mapOf(
                    "chatId" to chatId,
                    "reply_preview" to reply.take(40),
                    "notificationKey" to (notificationKey ?: "null")
                ))
                return Result.failure(
                    workDataOf(KEY_RESULT_MESSAGE to "Notification action failed")
                )
            }

            DebugLogger.logEvent(TAG, "SEND_SUCCESS", mapOf("chatId" to chatId))

            // Save the auto-reply message to database
            val autoReplyMessage = MessageEntity(
                chatId = chatId,
                direction = MessageDirection.BOT_OUTGOING,
                text = reply,
                timestamp = System.currentTimeMillis(),
                source = MessageSource.SYSTEM,
                senderName = "Auto-Reply Bot"
            )
            messageDao.insertMessage(autoReplyMessage)

            // Log the LLM interaction
            val llmLog = LLMLogEntity(
                chatId = chatId,
                inputContextSnippet = context.takeLast(3).joinToString("\n") { 
                    "${if (it.isIncoming) "Them" else "Me"}: ${it.text}"
                },
                generatedReply = reply,
                toneUsed = tone,
                timestamp = System.currentTimeMillis(),
                wasAutoSent = true,
                userFeedback = UserFeedback.NONE
            )
            llmLogDao.insertLog(llmLog)

            // Update chat's last LLM reply timestamp
            val chat = chatDao.getChatById(chatId)
            if (chat != null) {
                chatDao.updateChat(
                    chat.copy(
                        lastLLMReplyTimestamp = System.currentTimeMillis(),
                        lastMessageTimestamp = System.currentTimeMillis()
                    )
                )
            }

            DebugLogger.logEvent(TAG, "WORKER_COMPLETE_SUCCESS", mapOf(
                "chatId" to chatId,
                "reply_preview" to reply.take(40)
            ))

            return Result.success(
                workDataOf(
                    KEY_RESULT_SUCCESS to true,
                    KEY_RESULT_MESSAGE to "Auto-reply sent: $reply"
                )
            )

        } catch (e: Exception) {
            DebugLogger.logError(TAG, "WORKER_EXCEPTION", e, mapOf(
                "chatId" to chatId,
                "messageId" to messageId
            ))
            return Result.failure(
                workDataOf(KEY_RESULT_MESSAGE to "Error: ${e.message}")
            )
        }
    }

    /**
     * Re-validate that auto-reply is still enabled before sending
     */
    private suspend fun validateConditions(chatId: String): Boolean {
        // Check global kill switch
        val globalEnabled = settingsDao.getSetting("auto_reply_enabled")?.value?.toBoolean() ?: false
        if (!globalEnabled) {
            return false
        }

        // Check chat-level setting
        val chat = chatDao.getChatById(chatId)
        if (chat == null || !chat.autoReplyEnabled) {
            return false
        }

        return true
    }

    /**
     * Build conversation context
     */
    private suspend fun buildContext(chatId: String): List<ContextMessage> {
        val recentMessages = messageDao.getRecentMessagesForChat(chatId, 15)
        return recentMessages.reversed().map { msg ->
            ContextMessage(
                text = msg.text,
                isIncoming = msg.direction == MessageDirection.INCOMING,
                timestamp = msg.timestamp,
                senderName = msg.senderName
            )
        }
    }
}
