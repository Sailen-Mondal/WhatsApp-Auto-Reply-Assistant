package com.whatsappautoreply.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "llm_logs",
    indices = [Index(value = ["chatId"]), Index(value = ["timestamp"])]
)
data class LLMLogEntity(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0L,
    val chatId: String,
    val inputContextSnippet: String,
    val generatedReply: String,
    val toneUsed: String,
    val timestamp: Long = System.currentTimeMillis(),
    val wasAutoSent: Boolean = false,
    val userFeedback: UserFeedback = UserFeedback.NONE
)

enum class UserFeedback {
    UPVOTE,
    DOWNVOTE,
    NONE
}

