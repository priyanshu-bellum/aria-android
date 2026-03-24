package com.aria.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aria.data.claude.ClaudeApiClient
import com.aria.data.claude.PromptBuilder
import com.aria.data.claude.models.ClaudeMessageContent
import com.aria.data.local.dao.DaySummaryDao
import com.aria.data.local.entities.DaySummary
import com.aria.data.memory.Mem0Repository
import com.aria.data.memory.models.MemoryDelta
import com.aria.data.repository.SecureStorage
import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@HiltWorker
class SynthesisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val claudeApiClient: ClaudeApiClient,
    private val promptBuilder: PromptBuilder,
    private val mem0Repository: Mem0Repository,
    private val daySummaryDao: DaySummaryDao,
    private val moshi: Moshi,
    private val secureStorage: SecureStorage
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

            // 1. Load today's JSONL entries from PicoClaw logs
            val logsDir = File(applicationContext.filesDir, "logs")
            val logFile = File(logsDir, "aria.jsonl")
            val logEntries = if (logFile.exists()) logFile.readText() else ""

            if (logEntries.isBlank()) {
                return Result.success() // Nothing to synthesize
            }

            // 2. Load current profile
            val profileJson = mem0Repository.exportProfile()

            // 3. Build synthesis prompt
            val userName = secureStorage.getApiKey(SecureStorage.KEY_USER_NAME).takeIf { it?.isNotBlank() == true } ?: "there"
            val systemPrompt = promptBuilder.buildSynthesisPrompt(logEntries, profileJson, userName)

            // 4. Call Claude
            val response = claudeApiClient.sendMessage(
                systemPrompt = systemPrompt,
                messages = listOf(
                    ClaudeMessageContent(role = "user", content = "Analyze today's activity and update the profile.")
                ),
                maxTokens = 2000
            )

            val responseText = response.content.firstOrNull()?.text ?: return Result.retry()

            // 5. Parse JSON response
            val synthesisAdapter = moshi.adapter(SynthesisResultJson::class.java)
            val synthesis = synthesisAdapter.fromJson(responseText) ?: return Result.retry()

            // 6. Save summary to Room
            daySummaryDao.insert(
                DaySummary(
                    date = today,
                    summary = synthesis.summary,
                    rawJsonlPath = logFile.absolutePath
                )
            )

            // 7. Apply deltas to Mem0
            val deltas = synthesis.updates.map { update ->
                MemoryDelta(
                    layer = update.layer,
                    field = update.field,
                    newValue = update.new_value,
                    confidenceDelta = update.confidence_delta,
                    evidence = update.evidence
                )
            }
            mem0Repository.applyDeltas(deltas)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 1) Result.retry() else Result.failure()
        }
    }
}

// Internal JSON parsing models for synthesis response
private data class SynthesisResultJson(
    val summary: String = "",
    val updates: List<SynthesisUpdateJson> = emptyList()
)

private data class SynthesisUpdateJson(
    val layer: String = "",
    val field: String = "",
    val new_value: String = "",
    val confidence_delta: Float = 0f,
    val evidence: String = ""
)
