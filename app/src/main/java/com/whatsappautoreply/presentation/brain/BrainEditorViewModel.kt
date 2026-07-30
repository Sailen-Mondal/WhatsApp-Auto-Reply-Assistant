package com.whatsappautoreply.presentation.brain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsappautoreply.domain.brain.BrainFile
import com.whatsappautoreply.domain.brain.BrainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrainFileState(
    val brainFile: BrainFile,
    val content: String = "",
    val isSaving: Boolean = false,
    val isResetting: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BrainEditorViewModel @Inject constructor(
    private val brainRepository: BrainRepository
) : ViewModel() {

    private val _files = MutableStateFlow<List<BrainFileState>>(emptyList())
    val files: StateFlow<List<BrainFileState>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAllFiles()
    }

    private fun loadAllFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val states = BrainFile.editableFiles.map { brainFile ->
                BrainFileState(
                    brainFile = brainFile,
                    content = brainRepository.read(brainFile)
                )
            }
            _files.value = states
            _isLoading.value = false
        }
    }

    fun updateContent(brainFile: BrainFile, content: String) {
        _files.value = _files.value.map { state ->
            if (state.brainFile == brainFile) state.copy(content = content, saveSuccess = false, error = null)
            else state
        }
    }

    fun save(brainFile: BrainFile) {
        val current = _files.value.find { it.brainFile == brainFile } ?: return
        viewModelScope.launch {
            _files.value = _files.value.map { state ->
                if (state.brainFile == brainFile) state.copy(isSaving = true, error = null)
                else state
            }
            try {
                brainRepository.write(brainFile, current.content)
                _files.value = _files.value.map { state ->
                    if (state.brainFile == brainFile) state.copy(isSaving = false, saveSuccess = true)
                    else state
                }
            } catch (e: Exception) {
                _files.value = _files.value.map { state ->
                    if (state.brainFile == brainFile) state.copy(isSaving = false, error = "Save failed: ${e.message}")
                    else state
                }
            }
        }
    }

    fun resetToDefault(brainFile: BrainFile) {
        viewModelScope.launch {
            _files.value = _files.value.map { state ->
                if (state.brainFile == brainFile) state.copy(isResetting = true, error = null)
                else state
            }
            try {
                brainRepository.resetToDefault(brainFile)
                val newContent = brainRepository.read(brainFile)
                _files.value = _files.value.map { state ->
                    if (state.brainFile == brainFile) state.copy(isResetting = false, content = newContent, saveSuccess = false)
                    else state
                }
            } catch (e: Exception) {
                _files.value = _files.value.map { state ->
                    if (state.brainFile == brainFile) state.copy(isResetting = false, error = "Reset failed: ${e.message}")
                    else state
                }
            }
        }
    }

    fun refreshFile(brainFile: BrainFile) {
        viewModelScope.launch {
            val content = brainRepository.read(brainFile)
            _files.value = _files.value.map { state ->
                if (state.brainFile == brainFile) state.copy(content = content, saveSuccess = false, error = null)
                else state
            }
        }
    }
}
