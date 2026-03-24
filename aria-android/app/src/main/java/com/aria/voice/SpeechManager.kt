package com.aria.voice

import android.content.Context
import android.util.Log
import com.aria.data.claude.ClaudeApiClient
import com.aria.data.claude.PromptBuilder
import com.aria.data.claude.models.ClaudeMessageContent
import com.aria.data.local.dao.NoteDao
import com.aria.data.local.dao.TodoDao
import com.aria.data.local.entities.Note
import com.aria.data.local.entities.Todo
import com.aria.data.repository.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates offline/cloud STT and TTS for voice interaction.
 *
 * STT priority:
 * 1. [VoskSttEngine] (offline, ~50MB model required in filesDir or assets)
 * 2. Deepgram nova-2 cloud API (requires KEY_DEEPGRAM_API key in [SecureStorage])
 *
 * Transcripts are processed by Claude to extract todos/notes, which are persisted
 * to Room and confirmed via spoken TTS response.
 */
@Singleton
class SpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val claudeApiClient: ClaudeApiClient,
    private val promptBuilder: PromptBuilder,
    private val todoDao: TodoDao,
    private val noteDao: NoteDao,
    private val secureStorage: SecureStorage
) {
    private val stt = VoskSttEngine(context)
    private val tts = SherpaTtsEngine(context)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** OkHttpClient for Deepgram fallback — lightweight, no interceptors needed. */
    private val httpClient = OkHttpClient()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    companion object {
        private const val TAG = "SpeechManager"
        private const val DEEPGRAM_URL =
            "https://api.deepgram.com/v1/listen?model=nova-2&language=en"
        private const val AUDIO_CONTENT_TYPE = "audio/wav"
    }

    /**
     * Initialize both STT and TTS engines. Returns true if at least TTS is ready;
     * STT may fall back to Deepgram if Vosk model is unavailable.
     */
    suspend fun initialize(): Boolean {
        var success = false

        // Initialize TTS first — it's always available on Android
        try {
            val ttsOk = tts.initialize()
            if (ttsOk) {
                success = true
                Log.i(TAG, "TTS initialized")
            } else {
                Log.w(TAG, "TTS initialization failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing TTS", e)
        }

        // Initialize Vosk STT (graceful fallback to Deepgram if not ready)
        try {
            val sttOk = stt.initialize()
            if (sttOk) {
                Log.i(TAG, "Vosk STT initialized")
            } else {
                Log.w(TAG, "Vosk model unavailable — Deepgram fallback will be used for STT")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing Vosk STT", e)
        }

        return success
    }

    /**
     * Begin continuous voice listening. When Vosk is ready, audio is processed on-device.
     * When Vosk is not ready, the caller is responsible for routing audio bytes to
     * [transcribeWithDeepgram] (e.g., via an [AudioCaptureService] recording loop).
     *
     * Final transcripts trigger [processTranscript].
     */
    fun startListening() {
        if (_isListening.value) {
            Log.d(TAG, "Already listening — ignoring duplicate startListening call")
            return
        }

        if (stt.isReady()) {
            stt.startListening(
                onPartialResult = { partial ->
                    Log.d(TAG, "Partial: $partial")
                },
                onFinalResult = { transcript ->
                    _lastTranscript.value = transcript
                    _isListening.value = false
                    coroutineScope.launch {
                        processTranscript(transcript)
                    }
                }
            )
            _isListening.value = true
            Log.i(TAG, "Vosk listening started")
        } else {
            // Deepgram fallback: AudioCaptureService records audio and calls
            // transcribeWithDeepgram(audioBytes) directly. Flag listening as active
            // so callers know the service is in recording mode.
            _isListening.value = true
            Log.i(TAG, "Vosk not ready — listening mode active for Deepgram fallback")
        }
    }

    /** Stop the active STT session. */
    fun stopListening() {
        try {
            stt.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping STT", e)
        }
        _isListening.value = false
        Log.d(TAG, "Listening stopped")
    }

    /**
     * Speak [text] aloud via TTS. Updates [isSpeaking] for the duration of the utterance.
     */
    fun speak(text: String) {
        if (!tts.isReady()) {
            Log.w(TAG, "TTS not ready — cannot speak: \"$text\"")
            return
        }
        _isSpeaking.value = true
        tts.speak(text) {
            _isSpeaking.value = false
        }
    }

    /**
     * Transcribe raw audio [audioData] (PCM/WAV bytes) via Deepgram nova-2.
     * Used as fallback when Vosk model is not available.
     *
     * Returns the transcript string, or empty string on any failure.
     */
    suspend fun transcribeWithDeepgram(audioData: ByteArray): String {
        val apiKey = secureStorage.getApiKey(SecureStorage.KEY_DEEPGRAM_API)
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "Deepgram API key not configured — returning empty transcript")
            return ""
        }

        return try {
            val requestBody = audioData.toRequestBody(AUDIO_CONTENT_TYPE.toMediaType())
            val request = Request.Builder()
                .url(DEEPGRAM_URL)
                .header("Authorization", "Token $apiKey")
                .header("Content-Type", AUDIO_CONTENT_TYPE)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Deepgram API error ${response.code}: ${response.body?.string()}")
                    return ""
                }
                val body = response.body?.string() ?: return ""
                extractDeepgramTranscript(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deepgram transcription failed", e)
            ""
        }
    }

    /**
     * Extract transcript text from Deepgram's JSON response.
     *
     * Expected shape (simplified):
     * ```json
     * { "results": { "channels": [{ "alternatives": [{ "transcript": "hello world" }] }] } }
     * ```
     */
    private fun extractDeepgramTranscript(json: String): String {
        return try {
            // Walk the JSON structure with simple string extraction to avoid extra dependencies.
            val transcriptMarker = "\"transcript\""
            val markerIdx = json.indexOf(transcriptMarker)
            if (markerIdx < 0) return ""
            val colonIdx = json.indexOf(':', markerIdx + transcriptMarker.length)
            if (colonIdx < 0) return ""
            val quoteOpen = json.indexOf('"', colonIdx + 1)
            if (quoteOpen < 0) return ""
            val quoteClose = json.indexOf('"', quoteOpen + 1)
            if (quoteClose < 0) return ""
            json.substring(quoteOpen + 1, quoteClose).trim()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Deepgram response", e)
            ""
        }
    }

    /**
     * Process a finalized transcript:
     * 1. Build a todo-extraction prompt via [PromptBuilder].
     * 2. Send to Claude for parsing.
     * 3. Persist extracted todos and notes to Room.
     * 4. Speak a confirmation.
     */
    private suspend fun processTranscript(transcript: String) {
        if (transcript.isBlank()) return

        try {
            val systemPrompt = promptBuilder.buildTodoExtractionPrompt(transcript)

            val response = claudeApiClient.sendMessage(
                systemPrompt = systemPrompt,
                messages = listOf(
                    ClaudeMessageContent(
                        role = "user",
                        content = transcript
                    )
                ),
                maxTokens = 512
            )

            val responseText = response.content.firstOrNull()?.text
            if (responseText.isNullOrBlank()) {
                Log.w(TAG, "Claude returned empty response for transcript")
                return
            }

            val (todos, notes) = parseExtractionResponse(responseText)

            // Persist todos
            for (todoText in todos) {
                if (todoText.isBlank()) continue
                todoDao.insert(
                    Todo(
                        id = UUID.randomUUID().toString(),
                        text = todoText,
                        source = "voice",
                        completed = false,
                        createdAt = System.currentTimeMillis(),
                        dueDate = null
                    )
                )
            }

            // Persist notes
            for (noteText in notes) {
                if (noteText.isBlank()) continue
                noteDao.insert(
                    Note(
                        id = UUID.randomUUID().toString(),
                        text = noteText,
                        source = "voice",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            // Speak confirmation
            val confirmation = buildConfirmation(todos.size, notes.size)
            Log.i(TAG, "Processed transcript — ${todos.size} todos, ${notes.size} notes")
            speak(confirmation)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing transcript", e)
        }
    }

    /**
     * Parse Claude's JSON response for the todo-extraction prompt.
     *
     * Expected format:
     * ```json
     * { "todos": [{ "text": "...", "due": "..." }], "notes": ["..."] }
     * ```
     *
     * Returns a pair of (todoTexts, noteTexts). Uses simple string extraction to
     * avoid adding a JSON dependency beyond what's already in the project.
     */
    private fun parseExtractionResponse(json: String): Pair<List<String>, List<String>> {
        val todos = mutableListOf<String>()
        val notes = mutableListOf<String>()

        try {
            // Extract todos array content
            val todosStart = json.indexOf("\"todos\"")
            val notesStart = json.indexOf("\"notes\"")

            if (todosStart >= 0) {
                val arrayOpen = json.indexOf('[', todosStart)
                val arrayClose = findMatchingBracket(json, arrayOpen, '[', ']')
                if (arrayOpen >= 0 && arrayClose > arrayOpen) {
                    val todosSection = json.substring(arrayOpen, arrayClose + 1)
                    extractTextFields(todosSection, "text", todos)
                }
            }

            if (notesStart >= 0) {
                val arrayOpen = json.indexOf('[', notesStart)
                val arrayClose = findMatchingBracket(json, arrayOpen, '[', ']')
                if (arrayOpen >= 0 && arrayClose > arrayOpen) {
                    val notesSection = json.substring(arrayOpen, arrayClose + 1)
                    // Notes array contains bare strings, not objects
                    extractBareStrings(notesSection, notes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Claude extraction response: $json", e)
        }

        return Pair(todos, notes)
    }

    /**
     * Collect all values for the given [fieldName] key from a JSON snippet into [out].
     * Handles repeated occurrences (i.e., multiple objects in an array).
     */
    private fun extractTextFields(json: String, fieldName: String, out: MutableList<String>) {
        val marker = "\"$fieldName\""
        var searchFrom = 0
        while (true) {
            val markerIdx = json.indexOf(marker, searchFrom)
            if (markerIdx < 0) break
            val colonIdx = json.indexOf(':', markerIdx + marker.length)
            if (colonIdx < 0) break
            val quoteOpen = json.indexOf('"', colonIdx + 1)
            if (quoteOpen < 0) break
            val quoteClose = json.indexOf('"', quoteOpen + 1)
            if (quoteClose < 0) break
            val value = json.substring(quoteOpen + 1, quoteClose).trim()
            if (value.isNotBlank()) out.add(value)
            searchFrom = quoteClose + 1
        }
    }

    /**
     * Collect bare JSON string values from an array like `["note one", "note two"]`.
     */
    private fun extractBareStrings(json: String, out: MutableList<String>) {
        var searchFrom = 0
        // Skip the opening bracket
        val start = json.indexOf('[')
        if (start >= 0) searchFrom = start + 1

        while (true) {
            val quoteOpen = json.indexOf('"', searchFrom)
            if (quoteOpen < 0) break
            val quoteClose = json.indexOf('"', quoteOpen + 1)
            if (quoteClose < 0) break
            val value = json.substring(quoteOpen + 1, quoteClose).trim()
            if (value.isNotBlank()) out.add(value)
            searchFrom = quoteClose + 1
        }
    }

    /**
     * Find the index of the closing bracket that matches the opening bracket at [openIdx].
     * Returns -1 if not found.
     */
    private fun findMatchingBracket(
        json: String,
        openIdx: Int,
        open: Char,
        close: Char
    ): Int {
        if (openIdx < 0 || openIdx >= json.length) return -1
        var depth = 0
        for (i in openIdx until json.length) {
            when (json[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    /** Build a natural-language confirmation string based on what was saved. */
    private fun buildConfirmation(todoCount: Int, noteCount: Int): String {
        return when {
            todoCount > 0 && noteCount > 0 ->
                "Got it. I added $todoCount ${if (todoCount == 1) "todo" else "todos"} and $noteCount ${if (noteCount == 1) "note" else "notes"}."
            todoCount > 0 ->
                "Got it. I added $todoCount ${if (todoCount == 1) "todo" else "todos"}."
            noteCount > 0 ->
                "Noted. I saved $noteCount ${if (noteCount == 1) "note" else "notes"}."
            else ->
                "I heard you, but didn't find anything to save."
        }
    }

    /** Release all engine resources. */
    fun destroy() {
        try {
            stt.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying STT engine", e)
        }
        try {
            tts.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying TTS engine", e)
        }
        _isListening.value = false
        _isSpeaking.value = false
        Log.i(TAG, "SpeechManager destroyed")
    }
}
