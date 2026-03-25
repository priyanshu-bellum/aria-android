package com.aria.data.claude

import com.aria.data.claude.models.ClaudeMessageContent
import com.aria.data.claude.models.ClaudeRequest
import com.aria.data.claude.models.ClaudeResponse
import com.aria.data.claude.models.StreamEvent
import com.aria.data.repository.SecureStorage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader

class ClaudeApiClient(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val secureStorage: SecureStorage
) {
    private val baseUrl = "https://api.anthropic.com/v1/messages"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val apiVersion = "2023-06-01"

    private fun apiKey(): String =
        secureStorage.getApiKey(SecureStorage.KEY_CLAUDE_API)
            ?: throw IllegalStateException("Claude API key not configured")

    /**
     * Standard (non-streaming) completion.
     */
    suspend fun sendMessage(
        systemPrompt: String,
        messages: List<ClaudeMessageContent>,
        maxTokens: Int = 4096
    ): ClaudeResponse = withContext(Dispatchers.IO) {
        retryWithBackoff {
            val request = ClaudeRequest(
                system = systemPrompt,
                messages = messages,
                max_tokens = maxTokens,
                stream = false
            )
            val json = moshi.adapter(ClaudeRequest::class.java).toJson(request)
            val httpRequest = Request.Builder()
                .url(baseUrl)
                .header("x-api-key", apiKey())
                .header("anthropic-version", apiVersion)
                .header("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException(
                        "Claude API error ${response.code}: ${response.body?.string()}"
                    )
                }
                val body = response.body?.string() ?: ""
                moshi.adapter(ClaudeResponse::class.java).fromJson(body)
                    ?: throw RuntimeException("Failed to parse Claude response")
            }
        }
    }

    /**
     * Streaming completion using SSE. Calls onChunk for each text delta.
     */
    suspend fun streamMessage(
        systemPrompt: String,
        messages: List<ClaudeMessageContent>,
        maxTokens: Int = 4096,
        onChunk: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = ClaudeRequest(
            system = systemPrompt,
            messages = messages,
            max_tokens = maxTokens,
            stream = true
        )
        val json = moshi.adapter(ClaudeRequest::class.java).toJson(request)
        val httpRequest = Request.Builder()
            .url(baseUrl)
            .header("x-api-key", apiKey())
            .header("anthropic-version", apiVersion)
            .header("Content-Type", "application/json")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException(
                    "Claude API error ${response.code}: ${response.body?.string()}"
                )
            }
            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.startsWith("data: ")) {
                    val data = l.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val event = moshi.adapter(StreamEvent::class.java).fromJson(data)
                        val text = event?.delta?.text
                        if (!text.isNullOrEmpty()) {
                            onChunk(text)
                        }
                    } catch (_: Exception) {
                        // Skip malformed events
                    }
                }
            }
        }
    }

    /**
     * Retry with exponential backoff: 3 attempts, 1s/2s/4s delays.
     */
    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(1000L * (1 shl attempt)) // 1s, 2s, 4s
                }
            }
        }
        throw lastException ?: RuntimeException("Retry failed")
    }
}
