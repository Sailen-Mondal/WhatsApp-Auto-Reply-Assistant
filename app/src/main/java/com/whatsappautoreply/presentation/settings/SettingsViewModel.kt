package com.whatsappautoreply.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsappautoreply.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import com.whatsappautoreply.data.service.KeepAliveService
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _defaultTone = MutableStateFlow("auto")
    val defaultTone: StateFlow<String> = _defaultTone.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    private val _autoReplyEnabled = MutableStateFlow(false)
    val autoReplyEnabled: StateFlow<Boolean> = _autoReplyEnabled.asStateFlow()

    private val _minDelay = MutableStateFlow(1)
    val minDelay: StateFlow<Int> = _minDelay.asStateFlow()

    private val _maxDelay = MutableStateFlow(10)
    val maxDelay: StateFlow<Int> = _maxDelay.asStateFlow()

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

    fun loadSettings() {
        // Settings are loaded via Flow
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun updateDefaultTone(tone: String) {
        _defaultTone.value = tone
    }

    fun updateAutoReplyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setString("auto_reply_enabled", enabled.toString())
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



    fun saveSettings() {
        viewModelScope.launch {
            _isSaving.value = true
            _saveMessage.value = null

            try {
                if (_apiKey.value.isNotBlank()) {
                    settingsRepository.setString("llm_api_key", _apiKey.value.trim())
                } else {
                    // Clear if empty
                    settingsRepository.clear("llm_api_key")
                }

                settingsRepository.setString("default_tone", _defaultTone.value)
                // Other settings are saved immediately on change, so no need to save them here explicitly
                // unless we want to enforce a "Save" button paradigm.
                // But the UI calls update functions which save immediately.
                // So this button might be redundant for auto-reply settings, but useful for API key.
                
                _saveMessage.value = "Settings saved successfully!"
            } catch (e: Exception) {
                _saveMessage.value = "Error: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }
}


