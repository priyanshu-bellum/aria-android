package com.aria.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aria.data.claude.ClaudeApiClient
import com.aria.data.claude.PromptBuilder
import com.aria.data.memory.Mem0Repository
import com.aria.data.memory.models.PersonalityProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MirrorViewModel @Inject constructor(
    private val mem0Repository: Mem0Repository,
    private val claudeApiClient: ClaudeApiClient,
    private val promptBuilder: PromptBuilder
) : ViewModel() {

    private val _profile = MutableStateFlow<PersonalityProfile?>(null)
    val profile: StateFlow<PersonalityProfile?> = _profile

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _profile.value = mem0Repository.getFullProfile()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load profile"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun correctField(layer: String, field: String, newValue: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                mem0Repository.correctField(layer, field, newValue)
                loadProfile()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to update field"
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
