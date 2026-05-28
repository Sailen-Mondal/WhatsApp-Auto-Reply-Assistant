package com.whatsappautoreply.data.repository

import com.whatsappautoreply.data.database.dao.LLMLogDao
import com.whatsappautoreply.data.database.entity.LLMLogEntity
import com.whatsappautoreply.data.database.entity.UserFeedback
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMLogRepository @Inject constructor(
    private val llmLogDao: LLMLogDao
) {
    fun getLogsByChatId(chatId: String): Flow<List<LLMLogEntity>> =
        llmLogDao.getLogsByChatId(chatId)

    fun getRecentLogs(limit: Int = 50): Flow<List<LLMLogEntity>> =
        llmLogDao.getRecentLogs(limit)

    suspend fun insertLog(log: LLMLogEntity) {
        llmLogDao.insertLog(log)
    }

    suspend fun getLatestLogForChat(chatId: String): LLMLogEntity? {
        return llmLogDao.getLatestLogForChat(chatId)
    }

    suspend fun updateFeedback(logId: Long, feedback: UserFeedback) {
        llmLogDao.updateFeedback(logId, feedback)
    }
}

