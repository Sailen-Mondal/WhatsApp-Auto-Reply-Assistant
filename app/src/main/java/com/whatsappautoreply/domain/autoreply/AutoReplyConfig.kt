package com.whatsappautoreply.domain.autoreply

/**
 * Configuration for auto-reply behavior
 * Designed for natural 24/7 conversations without artificial limits
 */
data class AutoReplyConfig(
    val isGloballyEnabled: Boolean = false,
    val minDelaySeconds: Int = 1,      // Min delay: 1 second
    val maxDelaySeconds: Int = 10,     // Max delay: 10 seconds
    val excludeGroupChats: Boolean = true, // Don't auto-reply to groups (privacy/spam prevention)
    val cooldownSeconds: Int = 0,       // No cooldown - natural conversations can be rapid
    val quietHoursStart: Int? = null,
    val quietHoursEnd: Int? = null,
    val maxRepliesPerChat: Int = 5,
    val replyToQuestionsOnly: Boolean = false,
    val waitForUserSeconds: Int = 0,
    val safetyModeEnabled: Boolean = true,
    val maxRepliesPer10Min: Int = 3
)
