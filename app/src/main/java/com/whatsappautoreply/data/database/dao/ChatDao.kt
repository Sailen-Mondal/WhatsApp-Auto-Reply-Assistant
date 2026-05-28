package com.whatsappautoreply.data.database.dao

import androidx.room.*
import com.whatsappautoreply.data.database.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY lastMessageTimestamp DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    fun getChatByIdFlow(chatId: String): Flow<ChatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET lastMessageTimestamp = :timestamp WHERE chatId = :chatId")
    suspend fun updateLastMessageTimestamp(chatId: String, timestamp: Long)

    @Query("UPDATE chats SET autoReplyEnabled = :enabled WHERE chatId = :chatId")
    suspend fun updateAutoReplyEnabled(chatId: String, enabled: Boolean)

    @Query("UPDATE chats SET preferredTone = :tone WHERE chatId = :chatId")
    suspend fun updatePreferredTone(chatId: String, tone: String?)

    @Query("UPDATE chats SET lastLLMReplyTimestamp = :timestamp WHERE chatId = :chatId")
    suspend fun updateLastLLMReplyTimestamp(chatId: String, timestamp: Long)

    @Query("UPDATE chats SET autoReplyEnabled = 1 WHERE autoReplyEnabled = 0")
    suspend fun enableAllChatsAutoReply()

    @Delete
    suspend fun deleteChat(chat: ChatEntity)
}

