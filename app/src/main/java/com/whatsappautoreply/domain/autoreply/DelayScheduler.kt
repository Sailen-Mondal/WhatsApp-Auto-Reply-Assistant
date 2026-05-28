package com.whatsappautoreply.domain.autoreply

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.whatsappautoreply.data.worker.AutoReplyWorker
import com.whatsappautoreply.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules auto-replies with random delays using WorkManager
 */
@Singleton
class DelayScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DelayScheduler"
        private const val WORK_NAME_PREFIX = "auto_reply_"
    }

    /**
     * Schedule an auto-reply with delay
     * @param decision The auto-reply decision containing delay and context
     * @return WorkRequest ID if scheduled, null if not
     */
    fun scheduleAutoReply(decision: AutoReplyDecision): String? {
        if (!decision.shouldReply) {
            DebugLogger.logEvent(TAG, "SCHEDULE_SKIP", mapOf(
                "chatId" to decision.chatId,
                "reason" to decision.reason
            ))
            return null
        }

        val workName = "${WORK_NAME_PREFIX}${decision.chatId}_${System.currentTimeMillis()}"
        
        DebugLogger.logEvent(TAG, "SCHEDULING", mapOf(
            "chatId" to decision.chatId,
            "messageId" to decision.messageId,
            "delayMillis" to decision.delayMillis,
            "delaySeconds" to (decision.delayMillis / 1000),
            "tone" to (decision.suggestedTone ?: "auto"),
            "notificationKey" to (decision.notificationKey ?: "null"),
            "workName" to workName
        ))

        // Create work request with delay
        val workRequest = OneTimeWorkRequestBuilder<AutoReplyWorker>()
            .setInitialDelay(decision.delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    AutoReplyWorker.KEY_CHAT_ID to decision.chatId,
                    AutoReplyWorker.KEY_MESSAGE_ID to decision.messageId,
                    AutoReplyWorker.KEY_TONE to (decision.suggestedTone ?: "auto"),
                    AutoReplyWorker.KEY_MOOD to decision.suggestedMood,
                    AutoReplyWorker.KEY_NOTIFICATION_KEY to decision.notificationKey
                )
            )
            .addTag("auto_reply")
            .addTag("chat_${decision.chatId}")
            .build()

        // Enqueue the work
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        val workRequestId = workRequest.id.toString()
        DebugLogger.logEvent(TAG, "SCHEDULED_OK", mapOf(
            "chatId" to decision.chatId,
            "workRequestId" to workRequestId,
            "workName" to workName,
            "delaySeconds" to (decision.delayMillis / 1000)
        ))
        
        return workRequestId
    }

    /**
     * Cancel all pending auto-replies for a specific chat
     */
    fun cancelAutoRepliesForChat(chatId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("chat_$chatId")
        DebugLogger.logEvent(TAG, "CANCEL_CHAT", mapOf("chatId" to chatId))
    }

    /**
     * Cancel all pending auto-replies globally
     */
    fun cancelAllAutoReplies() {
        WorkManager.getInstance(context).cancelAllWorkByTag("auto_reply")
        DebugLogger.logEvent(TAG, "CANCEL_ALL")
    }
}
