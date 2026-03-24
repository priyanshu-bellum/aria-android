package com.aria.data.memory

import com.aria.data.repository.SecureStorage
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class Mem0ApiClient(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val secureStorage: SecureStorage
) {
    private val baseUrl = "https://api.mem0.ai/v1"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun apiKey(): String =
        secureStorage.getApiKey(SecureStorage.KEY_MEM0_API)
            ?: throw IllegalStateException("Mem0 API key not configured")

    private fun userId(): String =
        secureStorage.getApiKey(SecureStorage.KEY_MEM0_USER_ID) ?: "aria-user"

    /**
     * Add memories. Used for seeding profile and applying deltas.
     */
    suspend fun addMemory(text: String, metadata: Map<String, Any>? = null): String =
        withContext(Dispatchers.IO) {
            val body = buildMap {
                put("messages", listOf(mapOf("role" to "user", "content" to text)))
                put("user_id", userId())
                metadata?.let { put("metadata", it) }
            }
            withRetry { post("/memories", body) }
        }

    /**
     * Search memories by semantic query.
     */
    suspend fun searchMemories(query: String, limit: Int = 10): String =
        withContext(Dispatchers.IO) {
            val body = mapOf(
                "query" to query,
                "user_id" to userId(),
                "limit" to limit
            )
            withRetry { post("/memories/search", body) }
        }

    /**
     * Get all memories for the user.
     */
    suspend fun getAllMemories(): String =
        withContext(Dispatchers.IO) {
            withRetry { get("/memories?user_id=${userId()}") }
        }

    /**
     * Update a specific memory.
     */
    suspend fun updateMemory(memoryId: String, text: String): String =
        withContext(Dispatchers.IO) {
            val body = mapOf("text" to text)
            withRetry { put("/memories/$memoryId", body) }
        }

    /**
     * Delete all memories for the user (profile wipe).
     */
    suspend fun deleteAllMemories(): String =
        withContext(Dispatchers.IO) {
            withRetry { delete("/memories?user_id=${userId()}") }
        }

    // -- Retry helper --

    private suspend fun <T> withRetry(
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

    // -- HTTP helpers --

    private fun post(path: String, body: Any): String {
        val json = moshi.adapter(Any::class.java).toJson(body)
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Token ${apiKey()}")
            .header("Content-Type", "application/json")
            .post(json.toRequestBody(jsonMediaType))
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException(
                "Mem0 API error ${response.code}: ${response.body?.string()}"
            )
            response.body?.string() ?: ""
        }
    }

    private fun get(path: String): String {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Token ${apiKey()}")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException(
                "Mem0 API error ${response.code}: ${response.body?.string()}"
            )
            response.body?.string() ?: ""
        }
    }

    private fun put(path: String, body: Any): String {
        val json = moshi.adapter(Any::class.java).toJson(body)
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Token ${apiKey()}")
            .header("Content-Type", "application/json")
            .put(json.toRequestBody(jsonMediaType))
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException(
                "Mem0 API error ${response.code}: ${response.body?.string()}"
            )
            response.body?.string() ?: ""
        }
    }

    private fun delete(path: String): String {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Token ${apiKey()}")
            .delete()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException(
                "Mem0 API error ${response.code}: ${response.body?.string()}"
            )
            response.body?.string() ?: ""
        }
    }
}
