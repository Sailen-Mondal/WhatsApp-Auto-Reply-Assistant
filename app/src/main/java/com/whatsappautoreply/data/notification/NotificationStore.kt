package com.whatsappautoreply.data.notification

import android.service.notification.StatusBarNotification
import android.util.Log
import com.whatsappautoreply.util.DebugLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe storage for active WhatsApp notifications
 * Maps chatId to StatusBarNotification for reply action access
 */
@Singleton
class NotificationStore @Inject constructor() {
    private val mutex = Mutex()
    
    private data class NotificationEntry(
        val sbn: StatusBarNotification,
        val storedAt: Long = System.currentTimeMillis()
    )
    
    private val notifications = mutableMapOf<String, NotificationEntry>()

    companion object {
        private const val TAG = "NotificationStore"
        private const val MAX_NOTIFICATION_AGE_MS = 5 * 60 * 1000L
    }

    /**
     * Store a notification for a chat
     */
    suspend fun storeNotification(chatId: String, sbn: StatusBarNotification) = mutex.withLock {
        val existing = notifications[chatId]?.sbn
        val isReplacement = existing != null

        // CHECK: Don't overwrite an actionable notification with a non-actionable one (e.g. summary)
        var shouldStore = true
        if (existing != null) {
            val newHasReply = hasReplyAction(sbn)
            val oldHasReply = hasReplyAction(existing)

            if (!newHasReply && oldHasReply) {
                shouldStore = false
                DebugLogger.logEvent(TAG, "STORE_SKIPPED", mapOf(
                    "reason" to "keeping_actionable_notification",
                    "chatId" to chatId,
                    "new_key" to sbn.key
                ))
            }
        }

        if (shouldStore) {
            notifications[chatId] = NotificationEntry(sbn)

            DebugLogger.logEvent(TAG, "STORE", mapOf(
                "chatId" to chatId,
                "sbn_key" to sbn.key,
                "sbn_id" to sbn.id,
                "post_time" to sbn.postTime,
                "is_replacement" to isReplacement,
                "old_key" to (existing?.key ?: "none"),
                "old_post_time" to (existing?.postTime ?: 0),
                "actions_count" to (sbn.notification.actions?.size ?: 0),
                "has_reply_action" to hasReplyAction(sbn),
                "total_stored" to notifications.size
            ))
        }
    }

    /**
     * Get the stored notification for a chat
     */
    suspend fun getNotification(chatId: String): StatusBarNotification? = mutex.withLock {
        val entry = notifications[chatId] ?: return@withLock null
        val ageMs = System.currentTimeMillis() - entry.storedAt
        
        if (ageMs > MAX_NOTIFICATION_AGE_MS) {
            DebugLogger.logEvent(TAG, "STALE_NOTIFICATION_REMOVED", mapOf("chatId" to chatId, "age_ms" to ageMs))
            notifications.remove(chatId)
            return@withLock null
        }

        val sbn = entry.sbn
        DebugLogger.logEvent(TAG, "GET", mapOf(
            "chatId" to chatId,
            "found" to true,
            "sbn_key" to sbn.key,
            "age_ms" to ageMs,
            "actions_count" to (sbn.notification.actions?.size ?: 0),
            "has_reply_action" to hasReplyAction(sbn)
        ))

        sbn
    }

    /**
     * Remove a notification (e.g., when it's cleared or becomes stale)
     */
    suspend fun removeNotification(chatId: String) = mutex.withLock {
        val removed = notifications.remove(chatId)
        DebugLogger.logEvent(TAG, "REMOVE", mapOf(
            "chatId" to chatId,
            "was_present" to (removed != null),
            "removed_key" to (removed?.sbn?.key ?: "none"),
            "remaining" to notifications.size
        ))
    }

    /**
     * Clear all stored notifications
     */
    suspend fun clearAll() = mutex.withLock {
        val count = notifications.size
        notifications.clear()
        DebugLogger.logEvent(TAG, "CLEAR_ALL", mapOf("cleared_count" to count))
    }

    /**
     * Get count of stored notifications
     */
    suspend fun getCount(): Int = mutex.withLock {
        notifications.size
    }

    /**
     * Get the unique key of the stored notification for a chat
     */
    suspend fun getNotificationKey(chatId: String): String? = mutex.withLock {
        notifications[chatId]?.sbn?.key
    }
    
    suspend fun cleanupStaleNotifications() = mutex.withLock {
        val now = System.currentTimeMillis()
        val toRemove = notifications.filter { now - it.value.storedAt > MAX_NOTIFICATION_AGE_MS }.keys
        toRemove.forEach { notifications.remove(it) }
        if (toRemove.isNotEmpty()) {
            DebugLogger.logEvent(TAG, "CLEANUP_STALE", mapOf("removed_count" to toRemove.size))
        }
    }

    /**
     * Check if a StatusBarNotification has a reply action with RemoteInput
     */
    private fun hasReplyAction(sbn: StatusBarNotification): Boolean {
        val actions = sbn.notification.actions ?: return false
        return actions.any { action ->
            action.remoteInputs?.any { it.resultKey != null } == true
        }
    }
    
    // -- Live Fallback Mechanism --

    private var listenerServiceRef: java.lang.ref.WeakReference<android.service.notification.NotificationListenerService>? = null

    fun setListenerService(service: android.service.notification.NotificationListenerService) {
        listenerServiceRef = java.lang.ref.WeakReference(service)
    }

    fun clearListenerService() {
        listenerServiceRef = null
    }

    /**
     * Get notification with live fallback to active notifications if cache is missing or stale
     */
    suspend fun getNotificationWithFallback(chatId: String): StatusBarNotification? = mutex.withLock {
        val entry = notifications[chatId]
        var sbn = entry?.sbn
        var wasFallback = false
        val now = System.currentTimeMillis()
        val cacheAge = if (entry != null) now - entry.storedAt else -1

        // If missing or older than 60s, try to refresh from active notifications
        if (sbn == null || cacheAge > 60000) {
            val service = listenerServiceRef?.get()
            if (service != null) {
                try {
                    val activeNotifications = service.activeNotifications
                    // Find a notification that matches this chat ID
                    val found = activeNotifications.find { activeSbn ->
                        val title = activeSbn.notification.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                        title != null && com.whatsappautoreply.util.ChatUtils.generateChatId(title) == chatId
                    }

                    if (found != null) {
                        notifications[chatId] = NotificationEntry(found)
                        sbn = found
                        wasFallback = true
                    }
                } catch (e: Exception) {
                     DebugLogger.logError(TAG, "FALLBACK_ERROR", e)
                }
            } else {
                 DebugLogger.logWarning(TAG, "FALLBACK_SKIP", mapOf("reason" to "service_ref_null"))
            }
        }

        DebugLogger.logEvent(TAG, "GET_WITH_FALLBACK", mapOf(
            "chatId" to chatId,
            "found" to (sbn != null),
            "was_fallback" to wasFallback,
            "cache_age_ms" to cacheAge,
            "service_available" to (listenerServiceRef?.get() != null)
        ))

        return sbn
    }
}
