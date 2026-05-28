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

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(ChatFilter.ALL)
    val filter: StateFlow<ChatFilter> = _filter.asStateFlow()

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

