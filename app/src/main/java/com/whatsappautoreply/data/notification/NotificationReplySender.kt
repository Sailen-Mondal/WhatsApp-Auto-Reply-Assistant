package com.whatsappautoreply.data.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.whatsappautoreply.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles sending auto-replies via WhatsApp notification reply actions
 */
@Singleton
class NotificationReplySender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationStore: NotificationStore
) {
    companion object {
        private const val TAG = "NotifReplySender"
        private const val WHATSAPP_REPLY_KEY = "android.intent.extra.TEXT"
    }

    /**
     * Send an auto-reply to a chat via its notification
     * @param chatId The chat ID to reply to
     * @param replyText The text to send
     * @param expectedNotificationKey The key of the notification we expect to reply to (for validation)
     * @return true if sent successfully, false otherwise
     */
    suspend fun sendReply(chatId: String, replyText: String, expectedNotificationKey: String?): Boolean {
        DebugLogger.logEvent(TAG, "SEND_ATTEMPT_START", mapOf(
            "chatId" to chatId,
            "replyText_length" to replyText.length,
            "replyText_preview" to replyText.take(50),
            "expectedNotificationKey" to (expectedNotificationKey ?: "null")
        ))

        try {
            // Get the stored notification for this chat, with fallback to active notifications
            val sbn = notificationStore.getNotificationWithFallback(chatId)
            if (sbn == null) {
                DebugLogger.logError(TAG, "NO_NOTIFICATION_IN_STORE", data = mapOf(
                    "chatId" to chatId,
                    "reason" to "NotificationStore.getNotificationWithFallback returned null"
                ))
                return false
            }

            val notifAge = System.currentTimeMillis() - sbn.postTime
            DebugLogger.logEvent(TAG, "NOTIFICATION_FOUND", mapOf(
                "chatId" to chatId,
                "sbn_key" to sbn.key,
                "sbn_id" to sbn.id,
                "post_time" to sbn.postTime,
                "age_ms" to notifAge,
                "age_seconds" to (notifAge / 1000)
            ))

            // Pre-send validation: Ensure the notification is not older than 5 minutes (TTL)
            if (notifAge > 5 * 60 * 1000L) {
                DebugLogger.logError(TAG, "STALE_NOTIFICATION_ON_SEND", data = mapOf(
                    "chatId" to chatId,
                    "age_ms" to notifAge,
                    "reason" to "Notification is older than 5 minutes, cancelling send to prevent replying to old messages"
                ))
                notificationStore.removeNotification(chatId)
                return false
            }

            // Validate notification key if provided
            if (expectedNotificationKey != null && sbn.key != expectedNotificationKey) {
                DebugLogger.logWarning(TAG, "KEY_MISMATCH", mapOf(
                    "expected" to expectedNotificationKey,
                    "found" to sbn.key,
                    "proceeding_anyway" to true
                ))
            }

            // Find the Reply action
            val notification = sbn.notification
            val actions = notification.actions ?: emptyArray()
            DebugLogger.logEvent(TAG, "ACTIONS_SCAN", mapOf(
                "chatId" to chatId,
                "total_actions" to actions.size
            ))
            
            // Iterate through ALL actions to find one with RemoteInput
            var replyAction: Notification.Action? = null
            var remoteInput: RemoteInput? = null

            for ((index, action) in actions.withIndex()) {
                val title = action.title?.toString() ?: "null"
                val hasRemoteInput = action.remoteInputs != null && action.remoteInputs.isNotEmpty()
                
                DebugLogger.logEvent(TAG, "ACTION_DETAIL", mapOf(
                    "index" to index,
                    "title" to title,
                    "hasRemoteInput" to hasRemoteInput,
                    "remoteInputCount" to (action.remoteInputs?.size ?: 0)
                ))

                if (action.remoteInputs != null) {
                    for (input in action.remoteInputs) {
                        DebugLogger.logEvent(TAG, "REMOTE_INPUT_DETAIL", mapOf(
                            "action_index" to index,
                            "resultKey" to (input.resultKey ?: "null"),
                            "label" to (input.label?.toString() ?: "null"),
                            "allowFreeFormInput" to input.allowFreeFormInput
                        ))

                        if (input.resultKey != null) {
                            if (remoteInput == null || title.contains("Reply", ignoreCase = true)) {
                                remoteInput = input
                                replyAction = action
                            }
                        }
                    }
                }
            }

            if (replyAction == null || remoteInput == null) {
                DebugLogger.logError(TAG, "NO_REPLY_ACTION_FOUND", data = mapOf(
                    "chatId" to chatId,
                    "total_actions_scanned" to actions.size,
                    "notification_age_ms" to notifAge
                ))
                return false
            }

            DebugLogger.logEvent(TAG, "REPLY_ACTION_SELECTED", mapOf(
                "chatId" to chatId,
                "action_title" to (replyAction.title?.toString() ?: "null"),
                "resultKey" to remoteInput.resultKey
            ))

            // Create intent with reply text
            val localIntent = Intent()
            val localBundle = Bundle()
            localBundle.putCharSequence(remoteInput.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), localIntent, localBundle)

            // Send the reply
            try {
                DebugLogger.logEvent(TAG, "SENDING_PENDING_INTENT", mapOf(
                    "chatId" to chatId,
                    "replyText" to replyText.take(50),
                    "notification_age_ms" to notifAge
                ))

                replyAction.actionIntent.send(
                    /* context = */ context,
                    /* code = */ 0,
                    /* intent = */ localIntent
                )

                DebugLogger.logEvent(TAG, "SEND_SUCCESS", mapOf(
                    "chatId" to chatId,
                    "replyText_length" to replyText.length
                ))
                return true

            } catch (e: PendingIntent.CanceledException) {
                DebugLogger.logError(TAG, "PENDING_INTENT_CANCELLED", e, mapOf(
                    "chatId" to chatId,
                    "notification_age_ms" to notifAge,
                    "sbn_key" to sbn.key,
                    "hint" to "Notification was likely dismissed or updated before reply could be sent"
                ))
                // Remove stale notification from store
                notificationStore.removeNotification(chatId)
                return false
            }

        } catch (e: Exception) {
            DebugLogger.logError(TAG, "SEND_UNEXPECTED_ERROR", e, mapOf(
                "chatId" to chatId,
                "replyText_length" to replyText.length
            ))
            return false
        }
    }
}
