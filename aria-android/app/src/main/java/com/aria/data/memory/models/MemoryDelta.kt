package com.aria.data.memory.models

data class MemoryDelta(
    val layer: String,        // "behavioral_patterns" | "communication_style" | etc.
    val field: String,        // "decision_style" | "tone_professional" | etc.
    val newValue: String,     // new value (string or JSON array)
    val confidenceDelta: Float,
    val evidence: String      // one sentence explaining why
)

data class SynthesisResult(
    val summary: String,
    val updates: List<MemoryDelta>
)
