package com.whatsappautoreply.data.database.dao

import androidx.room.*
import com.whatsappautoreply.data.database.entity.LLMLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LLMLogDao {
    @Query("SELECT * FROM llm_logs WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getLogsByChatId(chatId: String): Flow<List<LLMLogEntity>>

    @Query("SELECT * FROM llm_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<LLMLogEntity>>

    @Query("SELECT * FROM llm_logs WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLogForChat(chatId: String): LLMLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LLMLogEntity)

    @Query("UPDATE llm_logs SET userFeedback = :feedback WHERE logId = :logId")
    suspend fun updateFeedback(logId: Long, feedback: com.whatsappautoreply.data.database.entity.UserFeedback)

    @Query("SELECT COUNT(*) FROM llm_logs")
    fun getTotalAutoReplies(): Flow<Int>

    @Query("DELETE FROM llm_logs WHERE logId IN (SELECT logId FROM llm_logs ORDER BY timestamp DESC LIMIT -1 OFFSET :limit)")
    suspend fun deleteOldLogs(limit: Int = 5000)

    @Query("SELECT toneUsed as tone, COUNT(*) as count FROM llm_logs GROUP BY toneUsed")
    fun getToneDistribution(): Flow<List<ToneStat>>
}

data class ToneStat(
    val tone: String,
    val count: Int
)

