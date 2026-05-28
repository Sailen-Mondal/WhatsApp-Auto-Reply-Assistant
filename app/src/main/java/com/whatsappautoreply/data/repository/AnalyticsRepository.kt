package com.whatsappautoreply.data.repository

import com.whatsappautoreply.data.database.dao.LLMLogDao
import com.whatsappautoreply.data.database.dao.MessageDao
import com.whatsappautoreply.data.database.dao.ToneStat
import com.whatsappautoreply.data.database.dao.DirectionStat
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val llmLogDao: LLMLogDao
) {
    fun getTotalMessages(): Flow<Int> = messageDao.getTotalMessages()

    fun getMessageDirectionStats(): Flow<List<DirectionStat>> = messageDao.getMessageDirectionStats()

    fun getTotalAutoReplies(): Flow<Int> = llmLogDao.getTotalAutoReplies()

    fun getToneDistribution(): Flow<List<ToneStat>> = llmLogDao.getToneDistribution()
}
