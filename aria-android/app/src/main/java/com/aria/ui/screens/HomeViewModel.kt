package com.aria.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aria.data.local.dao.TodoDao
import com.aria.data.local.entities.Todo
import com.aria.data.repository.SecureStorage
import com.aria.picoclaw.PicoClawManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val todoDao: TodoDao,
    private val secureStorage: SecureStorage,
    private val picoClawManager: PicoClawManager
) : ViewModel() {

    private val _greeting = MutableStateFlow("")
    val greeting: StateFlow<String> = _greeting

    private val _pendingTodos = MutableStateFlow<List<Todo>>(emptyList())
    val pendingTodos: StateFlow<List<Todo>> = _pendingTodos

    private val _picoClawStatus = MutableStateFlow(false)
    val picoClawStatus: StateFlow<Boolean> = _picoClawStatus

    init {
        updateGreeting()
        loadPendingTodos()
        startPicoClawStatusPolling()
    }

    private fun updateGreeting() {
        val name = secureStorage.getApiKey(SecureStorage.KEY_USER_NAME)
            ?.takeIf { it.isNotBlank() }
        val hour = LocalTime.now().hour
        val timeGreeting = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        _greeting.value = if (name != null) "$timeGreeting, $name" else timeGreeting
    }

    private fun loadPendingTodos() {
        viewModelScope.launch {
            try {
                _pendingTodos.value = todoDao.getTopPending(5)
            } catch (e: Exception) {
                _pendingTodos.value = emptyList()
            }
        }
    }

    private fun startPicoClawStatusPolling() {
        viewModelScope.launch {
            while (isActive) {
                _picoClawStatus.value = picoClawManager.isInstalled()
                delay(30_000L)
            }
        }
    }
}
