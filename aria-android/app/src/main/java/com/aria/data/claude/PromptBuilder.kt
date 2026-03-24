package com.aria.data.claude

import com.aria.data.local.dao.TodoDao
import com.aria.data.memory.Mem0Repository
import com.aria.data.memory.models.PersonalityProfile
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PromptBuilder(
    private val mem0Repository: Mem0Repository,
    private val todoDao: TodoDao
) {
    /**
     * Build assistant mode system prompt with full context.
     */
    suspend fun buildAssistantPrompt(
        profile: PersonalityProfile,
        userName: String,
        calendarEvents: String = "None scheduled"
    ): String {
        val todos = todoDao.getTopPending(5)
        val todosText = if (todos.isEmpty()) "None" else todos.joinToString("\n") { "- ${it.text}${if (it.dueDate != null) " (due: ${it.dueDate})" else ""}" }

        return """
You are ARIA, a personal AI assistant for $userName.

## Who $userName is
${formatProfile(profile)}

## Right now
- Date and time: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}
- Upcoming today: $calendarEvents
- Open todos:
$todosText

## Operating mode
assistant

In "assistant" mode: You act as a helpful, knowledgeable assistant who knows $userName deeply.

## Response style
- Be concise. $userName is busy.
- Match their communication style even in assistant mode.
- If you use a tool, confirm the action taken in plain language.
- Never mention "personality profile", "Mem0", or internal system details.
        """.trimIndent()
    }

    /**
     * Build proxy mode prompt (write AS the user).
     */
    fun buildProxyPrompt(profile: PersonalityProfile, userName: String): String = """
You are writing AS $userName. Match their tone, vocabulary, sentence structure, and formality level exactly as described in their communication style profile. Do not mention ARIA or that this was AI-generated.

## ${userName}'s communication style
- Professional tone: ${profile.layers.communicationStyle.toneProfessional}
- Personal tone: ${profile.layers.communicationStyle.tonePersonal}
- Vocabulary markers: ${profile.layers.communicationStyle.vocabularyMarkers.joinToString(", ")}
- Sentence structure: ${profile.layers.communicationStyle.sentenceStructure}
- Humor style: ${profile.layers.communicationStyle.humorStyle}
- Default formality: ${profile.layers.communicationStyle.formalityDefault}
    """.trimIndent()

    /**
     * Build nightly synthesis prompt.
     */
    fun buildSynthesisPrompt(logEntries: String, profileJson: String, userName: String): String = """
You are analyzing today's activity log for $userName.

## Today's activity log
$logEntries

## Current personality profile
$profileJson

## Your tasks
1. Write a 2–3 sentence summary of today (what happened, tone of day, notable events).
2. Identify signals from today's log that update, reinforce, or contradict the personality profile.
3. Only flag meaningful signals — ignore noise. A signal needs at least 2 data points to count.

## Return ONLY valid JSON in this exact format:
{
  "summary": "string",
  "updates": [
    {
      "layer": "behavioral_patterns | communication_style | core_identity | key_relationships | current_context",
      "field": "exact_field_name",
      "new_value": "string or array",
      "confidence_delta": 0.05,
      "evidence": "one sentence explaining why"
    }
  ]
}

Do not include any text outside the JSON block.
    """.trimIndent()

    /**
     * Build onboarding interview prompt.
     */
    fun buildOnboardingPrompt(): String = """
You are conducting a friendly onboarding interview for ARIA, a personal AI assistant.
Your goal is to understand this person well enough to fill in their personality profile.

Ask these 10 questions, one at a time, in a warm conversational way.
After each answer, acknowledge it briefly before moving to the next question.
Do not number the questions out loud.

Questions (ask in this order):
1. How do you prefer to start your mornings — structured routine or flexible?
2. When you have a packed calendar, what's your instinct — push through or cut things?
3. Describe how you write emails to colleagues vs close friends.
4. What's something you're genuinely working toward right now?
5. What do you value most in the people you work with?
6. When something goes wrong, what's your first reaction?
7. Are there words or phrases you find yourself saying a lot?
8. Who is the most important person in your professional life right now?
9. What's something most people get wrong about how you work?
10. What does a great day look like for you?

After the 10th answer, say:
"That gives me a great picture of who you are. I'm setting up your profile now — you'll be able to see and edit it any time from the Mirror screen."

Then return ONLY valid JSON:
{
  "name": "string (if mentioned)",
  "core_identity": { "values": [], "long_term_goals": [], "fears": [], "worldview": "", "identity_markers": [] },
  "behavioral_patterns": { "decision_style": "", "stress_response": "", "work_style": "", "risk_tolerance": "", "energy_pattern": "" },
  "communication_style": { "tone_professional": "", "tone_personal": "", "vocabulary_markers": [], "sentence_structure": "", "humor_style": "", "formality_default": "" },
  "key_relationships": [],
  "current_context": { "active_goals": [], "mood_trend": "", "current_stressors": [], "recent_wins": [], "pending_decisions": [] }
}
    """.trimIndent()

    /**
     * Build todo extraction prompt for voice transcriptions.
     */
    fun buildTodoExtractionPrompt(transcription: String): String = """
From the following transcribed speech, extract any action items, todos, reminders, or notes the speaker intended to remember.
Be conservative — only extract clear intentions, not general statements.

Transcription: "$transcription"

Return ONLY valid JSON:
{
  "todos": [
    { "text": "string", "due": "today | tomorrow | this week | null" }
  ],
  "notes": ["string"]
}

If nothing should be extracted, return: { "todos": [], "notes": [] }
    """.trimIndent()

    /**
     * Format the personality profile as readable text for the system prompt.
     */
    private fun formatProfile(profile: PersonalityProfile): String {
        val p = profile.layers
        return buildString {
            appendLine("### Core Identity")
            appendLine("Values: ${p.coreIdentity.values.joinToString(", ")}")
            appendLine("Long-term goals: ${p.coreIdentity.longTermGoals.joinToString(", ")}")
            appendLine("Worldview: ${p.coreIdentity.worldview}")
            appendLine("Identity markers: ${p.coreIdentity.identityMarkers.joinToString(", ")}")
            appendLine()
            appendLine("### Behavioral Patterns")
            appendLine("Decision style: ${p.behavioralPatterns.decisionStyle}")
            appendLine("Stress response: ${p.behavioralPatterns.stressResponse}")
            appendLine("Work style: ${p.behavioralPatterns.workStyle}")
            appendLine("Energy pattern: ${p.behavioralPatterns.energyPattern}")
            appendLine()
            appendLine("### Communication Style")
            appendLine("Professional tone: ${p.communicationStyle.toneProfessional}")
            appendLine("Personal tone: ${p.communicationStyle.tonePersonal}")
            appendLine("Common phrases: ${p.communicationStyle.vocabularyMarkers.joinToString(", ")}")
            appendLine("Sentence style: ${p.communicationStyle.sentenceStructure}")
            appendLine()
            appendLine("### Current Context")
            appendLine("Active goals: ${p.currentContext.activeGoals.joinToString(", ")}")
            appendLine("Mood trend: ${p.currentContext.moodTrend}")
            if (p.currentContext.currentStressors.isNotEmpty()) {
                appendLine("Current stressors: ${p.currentContext.currentStressors.joinToString(", ")}")
            }
            if (p.currentContext.recentWins.isNotEmpty()) {
                appendLine("Recent wins: ${p.currentContext.recentWins.joinToString(", ")}")
            }
        }
    }
}
