package com.whatsappautoreply.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["timestamp"]),
        Index(value = ["chatId", "text", "timestamp", "direction"], unique = true)
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val messageId: Long = 0L,
    val chatId: String,
    val senderName: String? = null,
    val direction: MessageDirection,
    val text: String? = null,
    val mediaType: MediaType = MediaType.TEXT,
    val mediaCaption: String? = null,
    val timestamp: Long,
    val source: MessageSource = MessageSource.NOTIFICATION,
    val rawNotificationId: Int? = null,
    val isDeletedInWhatsApp: Boolean = false
)

enum class MessageDirection {
    INCOMING,
    OUTGOING,
    BOT_OUTGOING
}

enum class MediaType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    STICKER,
    UNKNOWN
}

enum class MessageSource {
    NOTIFICATION,
    MANUAL_ENTRY,
    SYSTEM
}

