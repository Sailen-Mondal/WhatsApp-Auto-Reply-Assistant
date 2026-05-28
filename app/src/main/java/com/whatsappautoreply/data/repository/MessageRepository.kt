package com.whatsappautoreply.data.repository

import com.whatsappautoreply.data.database.dao.MessageDao
import com.whatsappautoreply.data.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao
) {
    fun getMessagesByChatId(chatId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesByChatId(chatId)

    suspend fun getRecentMessages(chatId: String, limit: Int = 10): List<MessageEntity> =
        messageDao.getRecentMessages(chatId, limit)
}

