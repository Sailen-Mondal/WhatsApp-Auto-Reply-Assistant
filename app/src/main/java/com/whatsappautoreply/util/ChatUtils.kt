package com.whatsappautoreply.util

import java.security.MessageDigest

object ChatUtils {
    /**
     * Generate a unique chat ID from a title (contact or group name).
     * This ensures consistency across notification processing and UI.
     */
    fun generateChatId(title: String): String {
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(title.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
