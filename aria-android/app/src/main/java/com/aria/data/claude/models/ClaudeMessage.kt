package com.aria.data.claude.models

data class ClaudeRequest(
    val model: String = "claude-sonnet-4-6",
    val max_tokens: Int = 4096,
    val system: String,
    val messages: List<ClaudeMessageContent>,
    val stream: Boolean = false
)

data class ClaudeMessageContent(
    val role: String,   // "user" | "assistant"
    val content: String
)

data class ClaudeResponse(
    val id: String = "",
    val type: String = "",
    val role: String = "",
    val content: List<ContentBlock> = emptyList(),
    val model: String = "",
    val stop_reason: String? = null,
    val usage: Usage? = null
)

data class ContentBlock(
    val type: String = "",   // "text"
    val text: String = ""
)

data class Usage(
    val input_tokens: Int = 0,
    val output_tokens: Int = 0
)

// SSE streaming events
data class StreamEvent(
    val type: String = "",
    val delta: StreamDelta? = null
)

data class StreamDelta(
    val type: String = "",
    val text: String = ""
)
