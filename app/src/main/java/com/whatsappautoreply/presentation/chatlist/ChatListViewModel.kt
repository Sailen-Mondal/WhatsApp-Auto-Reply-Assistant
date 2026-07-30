package com.whatsappautoreply.presentation.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsappautoreply.data.database.entity.ChatEntity
import com.whatsappautoreply.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.whatsappautoreply.data.repository.SettingsRepository

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(ChatFilter.ALL)
    val filter: StateFlow<ChatFilter> = _filter.asStateFlow()

    init {
        viewModelScope.launch {
            val globallyEnabled = settingsRepository.getString("auto_reply_enabled")?.toBoolean() ?: false
            if (globallyEnabled) {
                chatRepository.updateAllChatsAutoReply(true)
            }
        }
    }

    val chats: StateFlow<List<ChatEntity>> = combine(
        chatRepository.getAllChats(),
        _filter
    ) { chatList, currentFilter ->
        when (currentFilter) {
            ChatFilter.ALL -> chatList
            ChatFilter.AUTO_REPLY_ENABLED -> chatList.filter { it.autoReplyEnabled }
            ChatFilter.AUTO_REPLY_DISABLED -> chatList.filter { !it.autoReplyEnabled }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setFilter(newFilter: ChatFilter) {
        _filter.value = newFilter
    }

    fun toggleAutoReply(chatId: String, enabled: Boolean) {
        viewModelScope.launch {
            chatRepository.updateAutoReplyEnabled(chatId, enabled)
        }
    }
}

enum class ChatFilter {
    ALL,
    AUTO_REPLY_ENABLED,
    AUTO_REPLY_DISABLED
}

