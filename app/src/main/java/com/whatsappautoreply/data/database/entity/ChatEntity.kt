package com.whatsappautoreply.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val chatId: String,
    val title: String,
    val isGroup: Boolean = false,
    val autoReplyEnabled: Boolean = true,
    val preferredTone: String? = null, // auto, flirty, funny, professional, chill, romantic
    val lastMessageTimestamp: Long = 0L,
    val lastLLMReplyTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

