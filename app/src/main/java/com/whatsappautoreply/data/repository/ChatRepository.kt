package com.whatsappautoreply.data.repository

import com.whatsappautoreply.data.database.dao.ChatDao
import com.whatsappautoreply.data.database.entity.ChatEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    fun getAllChats(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    fun getChatById(chatId: String): Flow<ChatEntity?> = chatDao.getChatByIdFlow(chatId)

    suspend fun updateAutoReplyEnabled(chatId: String, enabled: Boolean) {
        chatDao.updateAutoReplyEnabled(chatId, enabled)
    }

    suspend fun updateAllChatsAutoReply(enabled: Boolean) {
        if (enabled) {
            chatDao.enableAllChatsAutoReply()
        } else {
            chatDao.disableAllChatsAutoReply()
        }
    }

    suspend fun updatePreferredTone(chatId: String, tone: String?) {
        chatDao.updatePreferredTone(chatId, tone)
    }
}

