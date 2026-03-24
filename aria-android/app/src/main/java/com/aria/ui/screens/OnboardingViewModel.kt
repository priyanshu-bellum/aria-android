package com.aria.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aria.data.claude.ClaudeApiClient
import com.aria.data.claude.PromptBuilder
import com.aria.data.claude.models.ClaudeMessageContent
import com.aria.data.memory.Mem0Repository
import com.aria.data.memory.models.PersonalityProfile
import com.aria.data.repository.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OnboardingStep {
    object Welcome : OnboardingStep()
    object Permissions : OnboardingStep()
    data class AccountSetup(val name: String = "", val phone: String = "") : OnboardingStep()
    object ApiKeys : OnboardingStep()
    object Interview : OnboardingStep()
    object Seeding : OnboardingStep()
    object Complete : OnboardingStep()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val claudeApiClient: ClaudeApiClient,
    private val mem0Repository: Mem0Repository,
    private val promptBuilder: PromptBuilder
) : ViewModel() {

    private val _step = MutableStateFlow<OnboardingStep>(OnboardingStep.Welcome)
    val step: StateFlow<OnboardingStep> = _step

    private val _chatMessages = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val chatMessages: StateFlow<List<Pair<String, String>>> = _chatMessages

    private val _streamingResponse = MutableStateFlow("")
    val streamingResponse: StateFlow<String> = _streamingResponse

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _interviewComplete = MutableStateFlow(false)
    val interviewComplete: StateFlow<Boolean> = _interviewComplete

    fun nextStep() {
        val current = _step.value
        _step.value = when (current) {
            is OnboardingStep.Welcome -> OnboardingStep.Permissions
            is OnboardingStep.Permissions -> OnboardingStep.AccountSetup()
            is OnboardingStep.AccountSetup -> OnboardingStep.ApiKeys
            is OnboardingStep.ApiKeys -> OnboardingStep.Interview
            is OnboardingStep.Interview -> OnboardingStep.Seeding
            is OnboardingStep.Seeding -> OnboardingStep.Complete
            is OnboardingStep.Complete -> OnboardingStep.Complete
        }
    }

    fun saveAccountInfo(name: String, phone: String) {
        viewModelScope.launch {
            try {
                secureStorage.saveApiKey(SecureStorage.KEY_USER_NAME, name.trim())
                secureStorage.saveApiKey(SecureStorage.KEY_USER_PHONE, phone.trim())
                _step.value = OnboardingStep.AccountSetup(name = name.trim(), phone = phone.trim())
            } catch (e: Exception) {
                // State stays on AccountSetup — UI can observe errorMessage if needed
            }
        }
    }

    fun saveApiKey(key: String, value: String) {
        viewModelScope.launch {
            try {
                secureStorage.saveApiKey(key, value.trim())
            } catch (e: Exception) {
                // Silently ignored — keys can be re-entered in Settings
            }
        }
    }

    fun startInterview() {
        viewModelScope.launch {
            _isLoading.value = true
            _streamingResponse.value = ""
            try {
                val systemPrompt = promptBuilder.buildOnboardingPrompt()
                val openingMessage = ClaudeMessageContent(
                    role = "user",
                    content = "Hello, I'm ready to get started."
                )
                val accumulatedText = StringBuilder()
                claudeApiClient.streamMessage(
                    systemPrompt = systemPrompt,
                    messages = listOf(openingMessage),
                    maxTokens = 512
                ) { chunk ->
                    accumulatedText.append(chunk)
                    _streamingResponse.value = accumulatedText.toString()
                }
                val responseText = accumulatedText.toString()
                _chatMessages.value = _chatMessages.value + listOf(
                    Pair("assistant", responseText)
                )
                _streamingResponse.value = ""
            } catch (e: Exception) {
                _streamingResponse.value = ""
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendAnswer(answer: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val updatedMessages = _chatMessages.value + listOf(Pair("user", answer))
            _chatMessages.value = updatedMessages
            _streamingResponse.value = ""

            try {
                val systemPrompt = promptBuilder.buildOnboardingPrompt()
                val apiMessages = updatedMessages.map { (role, content) ->
                    ClaudeMessageContent(role = role, content = content)
                }
                val accumulatedText = StringBuilder()
                claudeApiClient.streamMessage(
                    systemPrompt = systemPrompt,
                    messages = apiMessages,
                    maxTokens = 512
                ) { chunk ->
                    accumulatedText.append(chunk)
                    _streamingResponse.value = accumulatedText.toString()
                }
                val responseText = accumulatedText.toString()
                _chatMessages.value = _chatMessages.value + listOf(
                    Pair("assistant", responseText)
                )
                _streamingResponse.value = ""

                // Count user turns — each user message is one exchange
                val userTurns = _chatMessages.value.count { (role, _) -> role == "user" }
                if (userTurns >= 10) {
                    _interviewComplete.value = true
                }
            } catch (e: Exception) {
                _streamingResponse.value = ""
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            _isLoading.value = true
            _step.value = OnboardingStep.Seeding
            try {
                val profileJson = buildString {
                    append("{")
                    append("\"source\": \"onboarding_interview\",")
                    append("\"messages\": [")
                    _chatMessages.value.forEachIndexed { index, (role, content) ->
                        if (index > 0) append(",")
                        append("{\"role\": \"$role\", \"content\": ${content.replace("\"", "\\\"")}}")
                    }
                    append("]}")
                }
                mem0Repository.seedProfile(profileJson)
                secureStorage.markSetupComplete()
                _step.value = OnboardingStep.Complete
            } catch (e: Exception) {
                // Attempt to mark setup complete even if seeding fails
                try {
                    secureStorage.markSetupComplete()
                } catch (_: Exception) {}
                _step.value = OnboardingStep.Complete
            } finally {
                _isLoading.value = false
            }
        }
    }
}
