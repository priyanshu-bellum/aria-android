package com.aria.picoclaw

import android.content.Context
import com.aria.data.repository.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    /**
     * Generate PicoClaw config.json and write to a temp file.
     * Returns the file path. Caller should delete after PicoClaw starts.
     * JSON is used (valid YAML superset) to avoid a SnakeYAML dependency.
     */
    fun writeConfig(): File {
        val claudeKey = secureStorage.getApiKey(SecureStorage.KEY_CLAUDE_API) ?: ""
        val mem0Key = secureStorage.getApiKey(SecureStorage.KEY_MEM0_API) ?: ""
        val mem0UserId = secureStorage.getApiKey(SecureStorage.KEY_MEM0_USER_ID) ?: "aria-user"
        val telegramToken = secureStorage.getApiKey(SecureStorage.KEY_TELEGRAM_BOT_TOKEN)
        val composioKey = secureStorage.getApiKey(SecureStorage.KEY_COMPOSIO_API_KEY)
        val logsDir = File(context.filesDir, "logs").also { it.mkdirs() }

        val config = buildString {
            append("{")
            append("\"llm\":{\"provider\":\"anthropic\",\"model\":\"claude-sonnet-4-6\",\"api_key\":${jsonStr(claudeKey)}},")
            append("\"memory\":{\"provider\":\"mem0\",\"api_key\":${jsonStr(mem0Key)},\"user_id\":${jsonStr(mem0UserId)}},")

            if (!telegramToken.isNullOrBlank()) {
                append("\"gateways\":[{\"type\":\"telegram\",\"bot_token\":${jsonStr(telegramToken)}}],")
            }

            if (!composioKey.isNullOrBlank()) {
                append("\"mcp_servers\":[")
                append("{\"name\":\"google-calendar\",\"type\":\"http\",\"url\":\"https://mcp.composio.dev/googlecalendar\",\"api_key\":${jsonStr(composioKey)}},")
                append("{\"name\":\"gmail\",\"type\":\"http\",\"url\":\"https://mcp.composio.dev/gmail\",\"api_key\":${jsonStr(composioKey)}}")
                append("],")
            }

            append("\"crons\":[")
            append("{\"name\":\"nightly_synthesis\",\"schedule\":\"0 23 * * *\",\"action\":\"run_synthesis\"},")
            append("{\"name\":\"morning_call\",\"schedule\":\"0 8 * * *\",\"action\":\"trigger_morning_call\"}")
            append("],")

            append("\"logging\":{\"format\":\"jsonl\",\"path\":${jsonStr("${logsDir.absolutePath}/aria.jsonl")}}")
            append("}")
        }

        val configFile = File(context.cacheDir, "picoclaw_config.json")
        configFile.writeText(config)
        return configFile
    }

    /**
     * Delete the config file (call after PicoClaw has started).
     */
    fun deleteConfig() {
        val configFile = File(context.cacheDir, "picoclaw_config.json")
        if (configFile.exists()) configFile.delete()
    }

    private fun jsonStr(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
