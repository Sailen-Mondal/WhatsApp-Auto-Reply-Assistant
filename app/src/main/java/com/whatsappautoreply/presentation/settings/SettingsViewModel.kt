package com.whatsappautoreply.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsappautoreply.data.repository.SettingsRepository
import com.whatsappautoreply.data.service.KeepAliveService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.whatsappautoreply.data.repository.ChatRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    // ─── Reactive State Flows bound directly to Database ─────────────────────

    val apiKey: StateFlow<String> = settingsRepository.getStringFlow("api_key")
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val defaultTone: StateFlow<String> = settingsRepository.getStringFlow("default_tone")
        .map { it ?: "auto" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")

    val autoReplyEnabled: StateFlow<Boolean> = settingsRepository.getStringFlow("auto_reply_enabled")
        .map { it?.toBoolean() ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val minDelay: StateFlow<Int> = settingsRepository.getStringFlow("auto_reply_min_delay")
        .map { it?.toIntOrNull() ?: 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val maxDelay: StateFlow<Int> = settingsRepository.getStringFlow("auto_reply_max_delay")
        .map { it?.toIntOrNull() ?: 10 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val cooldown: StateFlow<Int> = settingsRepository.getStringFlow("auto_reply_cooldown")
        .map { it?.toIntOrNull() ?: 5 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val excludeGroupChats: StateFlow<Boolean> = settingsRepository.getStringFlow("exclude_group_chats")
        .map { it?.toBoolean() ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val replyToQuestionsOnly: StateFlow<Boolean> = settingsRepository.getStringFlow("reply_to_questions_only")
        .map { it?.toBoolean() ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val waitForUserSeconds: StateFlow<Int> = settingsRepository.getStringFlow("wait_for_user_seconds")
        .map { it?.toIntOrNull() ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val quietHoursEnabled: StateFlow<Boolean> = settingsRepository.getStringFlow("quiet_hours_enabled")
        .map { it?.toBoolean() ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val quietHoursStart: StateFlow<Int> = settingsRepository.getStringFlow("quiet_hours_start")
        .map { it?.toIntOrNull() ?: 22 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 22)

    val quietHoursEnd: StateFlow<Int> = settingsRepository.getStringFlow("quiet_hours_end")
        .map { it?.toIntOrNull() ?: 7 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    fun loadSettings() {
        // All settings load reactively via Flow
    }

    fun updateApiKey(key: String) {
        viewModelScope.launch {
            if (key.isNotBlank()) {
                settingsRepository.setString("api_key", key.trim())
            } else {
                settingsRepository.clear("api_key")
            }
        }
    }

    fun updateDefaultTone(tone: String) {
        viewModelScope.launch {
            settingsRepository.setString("default_tone", tone)
        }
    }

    fun updateAutoReplyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setString("auto_reply_enabled", enabled.toString())
            chatRepository.updateAllChatsAutoReply(enabled)
            if (enabled) {
                KeepAliveService.start(context)
            } else {
                KeepAliveService.stop(context)
            }
        }
    }

    fun updateMinDelay(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setString("auto_reply_min_delay", seconds.toString())
        }
    }

    fun updateMaxDelay(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setString("auto_reply_max_delay", seconds.toString())
        }
    }

    fun updateCooldown(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setString("auto_reply_cooldown", seconds.toString())
        }
    }

    fun updateExcludeGroupChats(exclude: Boolean) {
        viewModelScope.launch {
            settingsRepository.setString("exclude_group_chats", exclude.toString())
        }
    }

    fun updateReplyToQuestionsOnly(onlyQuestions: Boolean) {
        viewModelScope.launch {
            settingsRepository.setString("reply_to_questions_only", onlyQuestions.toString())
        }
    }

    fun updateWaitForUserSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setString("wait_for_user_seconds", seconds.toString())
        }
    }

    fun updateQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setString("quiet_hours_enabled", enabled.toString())
            if (!enabled) {
                settingsRepository.clear("quiet_hours_start")
                settingsRepository.clear("quiet_hours_end")
            } else {
                settingsRepository.setString("quiet_hours_start", quietHoursStart.value.toString())
                settingsRepository.setString("quiet_hours_end", quietHoursEnd.value.toString())
            }
        }
    }

    fun updateQuietHoursStart(hour: Int) {
        viewModelScope.launch {
            settingsRepository.setString("quiet_hours_start", hour.toString())
        }
    }

    fun updateQuietHoursEnd(hour: Int) {
        viewModelScope.launch {
            settingsRepository.setString("quiet_hours_end", hour.toString())
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = "Settings saved successfully!"
            kotlinx.coroutines.delay(1000)
            _isSaving.value = false
        }
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }
}
