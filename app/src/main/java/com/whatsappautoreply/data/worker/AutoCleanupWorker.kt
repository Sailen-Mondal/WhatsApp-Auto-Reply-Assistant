package com.whatsappautoreply.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whatsappautoreply.data.database.dao.LLMLogDao
import com.whatsappautoreply.data.database.dao.MessageDao
import com.whatsappautoreply.util.DebugLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AutoCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageDao: MessageDao,
    private val llmLogDao: LLMLogDao
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AutoCleanupWorker"
        const val WORK_NAME = "DatabaseAutoCleanupWork"
        const val MAX_MESSAGES_PER_CHAT = 1000
        const val MAX_LLM_LOGS = 5000
    }

    override suspend fun doWork(): Result {
        return try {
            DebugLogger.logEvent(TAG, "CLEANUP_STARTED")

            // 1. Clean up messages per chat
            val chatIds = messageDao.getAllChatIdsWithMessages()
            for (chatId in chatIds) {
                messageDao.deleteOldMessagesForChat(chatId, MAX_MESSAGES_PER_CHAT)
            }

            // 2. Clean up LLM Logs
            llmLogDao.deleteOldLogs(MAX_LLM_LOGS)

            DebugLogger.logEvent(TAG, "CLEANUP_COMPLETED", mapOf(
                "chats_processed" to chatIds.size,
                "message_limit_per_chat" to MAX_MESSAGES_PER_CHAT,
                "log_limit" to MAX_LLM_LOGS
            ))

            Result.success()
        } catch (e: Exception) {
            DebugLogger.logError(TAG, "CLEANUP_FAILED", e)
            Result.retry()
        }
    }
}
