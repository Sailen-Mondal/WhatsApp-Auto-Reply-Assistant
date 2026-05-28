package com.whatsappautoreply.data.database.dao

import androidx.room.*
import com.whatsappautoreply.data.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByChatIdPaginated(chatId: String, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(chatId: String, limit: Int = 10): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForChat(chatId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getMessagesForChatSince(chatId: String, since: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE timestamp >= :since AND direction = 'BOT_OUTGOING'")
    suspend fun getGlobalAutoReplyCountSince(since: Long): Int

    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET isDeletedInWhatsApp = :deleted WHERE messageId = :messageId")
    suspend fun markAsDeleted(messageId: Long, deleted: Boolean = true)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE messageId IN (SELECT messageId FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT -1 OFFSET :limit)")
    suspend fun deleteOldMessagesForChat(chatId: String, limit: Int = 1000)

    @Query("SELECT DISTINCT chatId FROM messages")
    suspend fun getAllChatIdsWithMessages(): List<String>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND (direction = 'OUTGOING' OR direction = 'BOT_OUTGOING') ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastOutgoingMessage(chatId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages")
    fun getTotalMessages(): Flow<Int>

    @Query("SELECT direction, COUNT(*) as count FROM messages GROUP BY direction")
    fun getMessageDirectionStats(): Flow<List<DirectionStat>>
}

data class DirectionStat(
    val direction: com.whatsappautoreply.data.database.entity.MessageDirection,
    val count: Int
)
