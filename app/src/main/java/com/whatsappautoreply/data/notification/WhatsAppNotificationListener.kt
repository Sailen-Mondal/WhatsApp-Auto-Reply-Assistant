package com.whatsappautoreply.data.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.os.Build
import com.whatsappautoreply.data.database.dao.SettingsDao
import com.whatsappautoreply.data.service.KeepAliveService
import com.whatsappautoreply.domain.autoreply.AutoReplyConfig
import com.whatsappautoreply.domain.autoreply.AutoReplyEngine
import com.whatsappautoreply.domain.autoreply.DelayScheduler
import com.whatsappautoreply.domain.brain.BrainRepository
import com.whatsappautoreply.util.ChatUtils
import com.whatsappautoreply.util.DebugLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.ComponentName
import javax.inject.Inject

@AndroidEntryPoint
class WhatsAppNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var notificationProcessor: NotificationProcessor

    @Inject
    lateinit var autoReplyEngine: AutoReplyEngine

    @Inject
    lateinit var delayScheduler: DelayScheduler

    @Inject
    lateinit var settingsDao: SettingsDao

    @Inject
    lateinit var notificationStore: NotificationStore

    @Inject
    lateinit var chatDao: com.whatsappautoreply.data.database.dao.ChatDao

    @Inject
    lateinit var messageDao: com.whatsappautoreply.data.database.dao.MessageDao

    @Inject
    lateinit var llmClient: com.whatsappautoreply.data.remote.llm.HuggingFaceLLMClient

    @Inject
    lateinit var notificationReplySender: NotificationReplySender

    @Inject
    lateinit var brainRepository: BrainRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Dedup: track last postTime we fired auto-reply for each chatId (prevents processing
    // the duplicate no-action notification WhatsApp fires 3ms after the real one)
    private val lastProcessedPostTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    companion object {
        private const val TAG = "WhatsAppNotifListener"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        // Any second notification for the same chat arriving within this window is a duplicate
        private const val DEDUP_WINDOW_MS = 2000L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        val packageName = sbn.packageName
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString()
        val text = extras?.getCharSequence("android.text")?.toString()
        val hasMessagingStyle = extras?.getParcelable<android.os.Parcelable>("android.messagingStyleUser") != null

        // Filter summary notifications
        if (sbn.tag?.contains("summary", ignoreCase = true) == true || text?.matches(Regex(".*\\d+ new messages.*")) == true) {
            DebugLogger.logEvent(TAG, "SKIPPED_SUMMARY", mapOf("title" to title))
            return
        }

        // Filter call notifications
        val actions = sbn.notification.actions
        if (actions != null && actions.any { it.title?.toString()?.contains("Answer", ignoreCase = true) == true || it.title?.toString()?.contains("Decline", ignoreCase = true) == true }) {
            DebugLogger.logEvent(TAG, "SKIPPED_CALL", mapOf("title" to title))
            return
        }

        // DEDUP: WhatsApp always fires a second "shadow" notification with 0 actions (no reply
        // capability) a few milliseconds after the real one. Drop it — we only need the one
        // that has a Reply action so we can actually send a reply later.
        val actionCount = sbn.notification.actions?.size ?: 0
        if (actionCount == 0) {
            DebugLogger.logEvent(TAG, "SKIPPED_NO_ACTIONS",
                mapOf("title" to title, "reason" to "shadow_notification_no_reply_action"))
            return
        }

        // DEDUP: drop any duplicate notification for the same chat within DEDUP_WINDOW_MS
        if (title != null) {
            val chatIdForDedup = com.whatsappautoreply.util.ChatUtils.generateChatId(title)
            val now = System.currentTimeMillis()
            val lastTime = lastProcessedPostTime[chatIdForDedup] ?: 0L
            if (now - lastTime < DEDUP_WINDOW_MS) {
                DebugLogger.logEvent(TAG, "SKIPPED_DEDUP",
                    mapOf("chatId" to chatIdForDedup, "ms_since_last" to (now - lastTime)))
                return
            }
            lastProcessedPostTime[chatIdForDedup] = now
        }

        DebugLogger.logEvent(TAG, "NOTIFICATION_POSTED", mapOf(
            "sbn_id" to sbn.id,
            "sbn_key" to sbn.key,
            "sbn_tag" to sbn.tag,
            "post_time" to sbn.postTime,
            "package" to packageName,
            "title" to title,
            "text" to (text?.take(80) ?: "null"),
            "has_messaging_style_hint" to hasMessagingStyle,
            "is_ongoing" to sbn.isOngoing,
            "notification_actions_count" to (sbn.notification.actions?.size ?: 0),
            "extras_keys" to (extras?.keySet()?.joinToString(",") ?: "none")
        ))

        serviceScope.launch {
            try {
                // Process notification and get incoming message ID if any
                val result = notificationProcessor.processNotification(sbn)
                
                if (result != null) {
                    val (chatId, messageId) = result
                    DebugLogger.logEvent(TAG, "NEW_INCOMING_MESSAGE", mapOf(
                        "chatId" to chatId,
                        "messageId" to messageId,
                        "will_trigger_auto_reply" to true
                    ))
                    
                    // Try to trigger auto-reply
                    triggerAutoReply(chatId, messageId)
                } else {
                    DebugLogger.logEvent(TAG, "NO_NEW_INCOMING", mapOf(
                        "reason" to "processNotification returned null (no new incoming message or duplicate)"
                    ))
                }
            } catch (e: Exception) {
                DebugLogger.logError(TAG, "NOTIFICATION_PROCESSING_ERROR", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        
        val packageName = sbn.packageName
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) {
            return
        }

        // Remove from notification store when notification is dismissed
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString()

        DebugLogger.logEvent(TAG, "NOTIFICATION_REMOVED", mapOf(
            "sbn_id" to sbn.id,
            "sbn_key" to sbn.key,
            "title" to title,
            "post_time" to sbn.postTime
        ))

        if (title != null) {
            serviceScope.launch {
                try {
                    val chatId = ChatUtils.generateChatId(title)
                    // notificationStore.removeNotification(chatId)
                    DebugLogger.logEvent(TAG, "NOTIFICATION_REMOVED_IGNORED", mapOf(
                        "chatId" to chatId,
                        "title" to title,
                        "reason" to "Persisting logic for delayed auto-replies"
                    ))
                } catch (e: Exception) {
                    DebugLogger.logError(TAG, "NOTIFICATION_REMOVE_ERROR", e)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        DebugLogger.logEvent(TAG, "LISTENER_CONNECTED")
        notificationStore.setListenerService(this)
        
        serviceScope.launch {
            // Initialize brain files on first run (idempotent)
            brainRepository.initializeIfNeeded()
            DebugLogger.logEvent(TAG, "BRAIN_INITIALIZED")



            val config = loadAutoReplyConfig()
            DebugLogger.logEvent(TAG, "CONFIG_LOADED", mapOf(
                "globallyEnabled" to config.isGloballyEnabled,
                "minDelay" to config.minDelaySeconds,
                "maxDelay" to config.maxDelaySeconds,
                "cooldown" to config.cooldownSeconds
            ))
            if (config.isGloballyEnabled) {
                KeepAliveService.start(applicationContext)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        DebugLogger.logEvent(TAG, "LISTENER_DISCONNECTED")
        notificationStore.clearListenerService()
        
        // Reconnection logic
        serviceScope.launch {
            repeat(3) { attempt ->
                delay(5000L) // Wait 5 seconds before retrying
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        requestRebind(ComponentName(applicationContext, WhatsAppNotificationListener::class.java))
                        DebugLogger.logEvent(TAG, "REBIND_REQUESTED", mapOf("attempt" to attempt + 1))
                        return@launch
                    }
                } catch (e: Exception) {
                    DebugLogger.logError(TAG, "REBIND_FAILED", e)
                }
            }
        }
    }

    /**
     * Attempt to trigger auto-reply for a message.
     * Uses a direct coroutine delay instead of WorkManager — WorkManager has 9-15 second
     * hidden overhead even for sub-second delays due to Android battery optimization.
     * The NotificationListenerService is already a foreground service so the coroutine
     * is guaranteed to run within the specified delay window.
     */
    private suspend fun triggerAutoReply(chatId: String, messageId: Long) {
        try {
            val config = loadAutoReplyConfig()
            if (!config.isGloballyEnabled) {
                DebugLogger.logEvent(TAG, "AUTO_REPLY_SKIP", mapOf("reason" to "globally_disabled", "chatId" to chatId))
                return
            }

            val notificationKey = notificationStore.getNotificationKey(chatId)
            DebugLogger.logEvent(TAG, "AUTO_REPLY_EVALUATE", mapOf(
                "chatId" to chatId,
                "messageId" to messageId,
                "notificationKey" to (notificationKey ?: "NONE")
            ))

            val decision = autoReplyEngine.evaluateMessage(chatId, messageId, config, notificationKey)

            if (!decision.shouldReply) {
                DebugLogger.logEvent(TAG, "AUTO_REPLY_REJECTED", mapOf("chatId" to chatId, "reason" to decision.reason))
                return
            }

            DebugLogger.logEvent(TAG, "AUTO_REPLY_APPROVED", mapOf(
                "chatId" to chatId,
                "reason" to decision.reason,
                "delayMillis" to decision.delayMillis,
                "tone" to (decision.suggestedTone ?: "auto")
            ))

            // ─── Coroutine-based delay + generate + send ────────────────────────
            // This fires reliably within the exact delay window without WorkManager overhead.
            val resolvedTone  = decision.suggestedTone  ?: "auto"
            val resolvedMood  = decision.suggestedMood
            val delayMs       = decision.delayMillis
            val capturedKey   = notificationKey

            serviceScope.launch {
                DebugLogger.logEvent(TAG, "COROUTINE_REPLY_WAITING", mapOf(
                    "chatId" to chatId, "delayMs" to delayMs
                ))
                delay(delayMs)

                try {
                    // Re-validate global kill-switch after the delay
                    val stillEnabled = settingsDao.getSetting("auto_reply_enabled")?.value?.toBoolean() ?: false
                    val chatStillEnabled = chatDao.getChatById(chatId)?.autoReplyEnabled ?: false
                    if (!stillEnabled || !chatStillEnabled) {
                        DebugLogger.logEvent(TAG, "COROUTINE_REPLY_CANCELLED", mapOf(
                            "chatId" to chatId, "reason" to "disabled_after_delay"
                        ))
                        return@launch
                    }

                    // Build context from recent messages
                    val recentMsgs = messageDao.getRecentMessagesForChat(chatId, 15)
                    val context = recentMsgs.reversed().map { msg ->
                        com.whatsappautoreply.domain.autoreply.ContextMessage(
                            text      = msg.text,
                            isIncoming = msg.direction == com.whatsappautoreply.data.database.entity.MessageDirection.INCOMING,
                            timestamp  = msg.timestamp,
                            senderName = msg.senderName
                        )
                    }

                    DebugLogger.logEvent(TAG, "COROUTINE_GENERATING_REPLY", mapOf(
                        "chatId" to chatId, "contextSize" to context.size, "tone" to resolvedTone
                    ))

                    val reply = llmClient.generateReply(context, resolvedTone, resolvedMood)

                    if (reply == null) {
                        DebugLogger.logEvent(TAG, "COROUTINE_LLM_NULL", mapOf("chatId" to chatId))
                        return@launch
                    }

                    DebugLogger.logEvent(TAG, "COROUTINE_REPLY_READY", mapOf(
                        "chatId" to chatId, "reply" to reply.take(60)
                    ))

                    val sent = notificationReplySender.sendReply(chatId, reply, capturedKey)

                    if (sent) {
                        DebugLogger.logEvent(TAG, "COROUTINE_SEND_SUCCESS", mapOf(
                            "chatId" to chatId, "reply" to reply.take(60)
                        ))
                        // Save bot reply to DB
                        messageDao.insertMessage(
                            com.whatsappautoreply.data.database.entity.MessageEntity(
                                chatId    = chatId,
                                direction = com.whatsappautoreply.data.database.entity.MessageDirection.BOT_OUTGOING,
                                text      = reply,
                                timestamp = System.currentTimeMillis(),
                                source    = com.whatsappautoreply.data.database.entity.MessageSource.SYSTEM,
                                senderName = "Auto-Reply Bot"
                            )
                        )
                        chatDao.updateLastLLMReplyTimestamp(chatId, System.currentTimeMillis())
                    } else {
                        DebugLogger.logError(TAG, "COROUTINE_SEND_FAILED", data = mapOf(
                            "chatId" to chatId, "capturedKey" to (capturedKey ?: "null")
                        ))
                    }
                } catch (e: Exception) {
                    DebugLogger.logError(TAG, "COROUTINE_REPLY_EXCEPTION", e, mapOf("chatId" to chatId))
                }
            }

        } catch (e: Exception) {
            DebugLogger.logError(TAG, "AUTO_REPLY_TRIGGER_ERROR", e, mapOf(
                "chatId" to chatId, "messageId" to messageId
            ))
        }
    }

    /**
     * Load auto-reply configuration from settings.
     * Loads ALL 12 AutoReplyConfig fields (previously only 7 were loaded).
     */
    private suspend fun loadAutoReplyConfig(): AutoReplyConfig {
        val enabled        = settingsDao.getSetting("auto_reply_enabled")?.value?.toBoolean() ?: false
        val minDelay       = settingsDao.getSetting("auto_reply_min_delay")?.value?.toIntOrNull() ?: 1
        val maxDelay       = settingsDao.getSetting("auto_reply_max_delay")?.value?.toIntOrNull() ?: 10
        val cooldown       = settingsDao.getSetting("auto_reply_cooldown")?.value?.toIntOrNull() ?: 0
        val excludeGroups  = settingsDao.getSetting("exclude_group_chats")?.value?.toBoolean() ?: true
        val replyToQuestions = settingsDao.getSetting("reply_to_questions_only")?.value?.toBoolean() ?: false
        val waitForUser    = settingsDao.getSetting("wait_for_user_seconds")?.value?.toIntOrNull() ?: 0
        // Previously missing fields — now loaded correctly:
        val quietHoursStart = settingsDao.getSetting("quiet_hours_start")?.value?.toIntOrNull()
        val quietHoursEnd   = settingsDao.getSetting("quiet_hours_end")?.value?.toIntOrNull()
        val safetyMode      = settingsDao.getSetting("safety_mode_enabled")?.value?.toBoolean() ?: true
        val maxPer10Min     = settingsDao.getSetting("max_replies_per_10min")?.value?.toIntOrNull() ?: 3

        return AutoReplyConfig(
            isGloballyEnabled    = enabled,
            minDelaySeconds      = minDelay,
            maxDelaySeconds      = maxDelay,
            cooldownSeconds      = cooldown,
            excludeGroupChats    = excludeGroups,
            replyToQuestionsOnly = replyToQuestions,
            waitForUserSeconds   = waitForUser,
            quietHoursStart      = quietHoursStart,
            quietHoursEnd        = quietHoursEnd,
            safetyModeEnabled    = safetyMode,
            maxRepliesPer10Min   = maxPer10Min
        )
    }

}
