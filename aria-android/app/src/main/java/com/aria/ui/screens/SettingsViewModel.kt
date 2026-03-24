package com.aria.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aria.data.memory.Mem0Repository
import com.aria.data.repository.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val mem0Repository: Mem0Repository
) : ViewModel() {

    private val _userName = MutableStateFlow(
        secureStorage.getApiKey(SecureStorage.KEY_USER_NAME) ?: ""
    )
    val userName: StateFlow<String> = _userName

    private val _isWiping = MutableStateFlow(false)
    val isWiping: StateFlow<Boolean> = _isWiping

    private val _exportData = MutableStateFlow<String?>(null)
    val exportData: StateFlow<String?> = _exportData

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage

    fun saveKey(key: String, value: String) {
        viewModelScope.launch {
            try {
                secureStorage.saveApiKey(key, value.trim())
                if (key == SecureStorage.KEY_USER_NAME) {
                    _userName.value = value.trim()
                }
                _snackbarMessage.value = "Saved"
            } catch (e: Exception) {
                _snackbarMessage.value = e.message ?: "Failed to save"
            }
        }
    }

    fun getKey(key: String): String {
        return secureStorage.getApiKey(key) ?: ""
    }

    fun exportProfile() {
        viewModelScope.launch {
            try {
                val json = mem0Repository.exportProfile()
                _exportData.value = json
            } catch (e: Exception) {
                _snackbarMessage.value = e.message ?: "Export failed"
            }
        }
    }

    fun wipeAll() {
        viewModelScope.launch {
            _isWiping.value = true
            try {
                secureStorage.clearAll()
                mem0Repository.wipeProfile()
                _snackbarMessage.value = "Profile wiped"
            } catch (e: Exception) {
                _snackbarMessage.value = e.message ?: "Wipe failed"
            } finally {
                _isWiping.value = false
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearExport() {
        _exportData.value = null
    }
}
