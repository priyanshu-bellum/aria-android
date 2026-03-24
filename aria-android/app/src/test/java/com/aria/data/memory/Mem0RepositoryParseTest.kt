package com.aria.data.memory

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Mem0RepositoryParseTest {

    private val mockApiClient: Mem0ApiClient = mockk(relaxed = true)

    // Real Moshi — no Android context required; KotlinJsonAdapterFactory handles reflection
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private lateinit var repository: Mem0Repository

    @Before
    fun setup() {
        repository = Mem0Repository(mockApiClient, moshi)
    }

    // -------------------------------------------------------------------------
    // Test 1 — empty results list returns default PersonalityProfile
    // -------------------------------------------------------------------------

    @Test
    fun `getFullProfile_withEmptyJson_returnsDefaultProfile`() = runTest {
        coEvery { mockApiClient.getAllMemories() } returns """{"results":[]}"""

        val profile = repository.getFullProfile()

        assertNotNull("Profile must not be null", profile)
        // Default profile has empty layers
        assertTrue(
            "Core identity values should be empty",
            profile.layers.coreIdentity.values.isEmpty()
        )
        assertTrue(
            "Behavioral patterns decision style should be blank",
            profile.layers.behavioralPatterns.decisionStyle.isBlank()
        )
        assertEquals("Confidence scores should all be zero", 0f, profile.confidenceScores.coreIdentity)
    }

    // -------------------------------------------------------------------------
    // Test 2 — full personality_profile blob is parsed into the profile layers
    // -------------------------------------------------------------------------

    @Test
    fun `getFullProfile_withProfileBlob_parsesValues`() = runTest {
        // This is the format written by Mem0Repository.seedProfile()
        val profileJson = """
            {
              "core_identity": {
                "values": ["integrity", "growth"],
                "long_term_goals": ["build impactful products"],
                "fears": [],
                "worldview": "Optimistic pragmatist",
                "identity_markers": ["builder"]
              },
              "behavioral_patterns": {
                "decision_style": "analytical",
                "stress_response": "solo deep work",
                "work_style": "focus blocks",
                "risk_tolerance": "medium",
                "energy_pattern": "morning"
              },
              "communication_style": {
                "tone_professional": "direct",
                "tone_personal": "warm",
                "vocabulary_markers": ["honestly"],
                "sentence_structure": "short",
                "humor_style": "dry wit",
                "formality_default": "semi-formal"
              },
              "key_relationships": [],
              "current_context": {
                "active_goals": ["ship v1"],
                "mood_trend": "focused",
                "current_stressors": [],
                "recent_wins": ["closed deal"],
                "pending_decisions": []
              }
            }
        """.trimIndent()

        val mem0Response = """
            {
              "results": [
                {
                  "id": "mem-001",
                  "memory": "User personality profile: $profileJson",
                  "metadata": { "type": "personality_profile", "source": "onboarding" }
                }
              ]
            }
        """.trimIndent()

        coEvery { mockApiClient.getAllMemories() } returns mem0Response

        val profile = repository.getFullProfile()

        assertEquals(
            "Core identity worldview should be parsed",
            "Optimistic pragmatist",
            profile.layers.coreIdentity.worldview
        )
        assertTrue(
            "Core identity values should contain 'integrity'",
            profile.layers.coreIdentity.values.contains("integrity")
        )
        assertEquals(
            "Decision style should be parsed",
            "analytical",
            profile.layers.behavioralPatterns.decisionStyle
        )
        assertEquals(
            "Tone professional should be parsed",
            "direct",
            profile.layers.communicationStyle.toneProfessional
        )
        assertTrue(
            "Active goals should contain 'ship v1'",
            profile.layers.currentContext.activeGoals.contains("ship v1")
        )
    }

    // -------------------------------------------------------------------------
    // Test 3 — individual delta entries are merged into the profile
    // -------------------------------------------------------------------------

    @Test
    fun `getFullProfile_withDeltaEntries_mergesCorrectly`() = runTest {
        // Two separate profile_delta entries — later one should override earlier for same key
        val mem0Response = """
            {
              "results": [
                {
                  "id": "mem-002",
                  "memory": "Profile update — core_identity.worldview: Cautious realist. Evidence: user mentioned preferring safe bets.",
                  "metadata": {
                    "type": "profile_delta",
                    "layer": "core_identity",
                    "field": "worldview",
                    "confidence_delta": 0.1
                  }
                },
                {
                  "id": "mem-003",
                  "memory": "Profile update — behavioral_patterns.decision_style: data-driven. Evidence: always asks for metrics first.",
                  "metadata": {
                    "type": "profile_delta",
                    "layer": "behavioral_patterns",
                    "field": "decision_style",
                    "confidence_delta": 0.15
                  }
                },
                {
                  "id": "mem-004",
                  "memory": "User correction — core_identity.worldview: Pragmatic optimist (confidence: 1.0)",
                  "metadata": {
                    "type": "user_correction",
                    "layer": "core_identity",
                    "field": "worldview",
                    "confidence": 1.0
                  }
                }
              ]
            }
        """.trimIndent()

        coEvery { mockApiClient.getAllMemories() } returns mem0Response

        val profile = repository.getFullProfile()

        // The user_correction (last entry) should win over the earlier profile_delta
        assertEquals(
            "User correction should override the earlier delta for worldview",
            "Pragmatic optimist",
            profile.layers.coreIdentity.worldview
        )
        assertEquals(
            "Decision style delta should be applied",
            "data-driven",
            profile.layers.behavioralPatterns.decisionStyle
        )
    }

    // -------------------------------------------------------------------------
    // Test 4 — blank / malformed JSON returns default profile without throwing
    // -------------------------------------------------------------------------

    @Test
    fun `getFullProfile_withBlankResponse_returnsDefaultWithoutThrowing`() = runTest {
        coEvery { mockApiClient.getAllMemories() } returns "   "

        val profile = repository.getFullProfile()

        assertNotNull("Profile must not be null for blank response", profile)
        assertTrue(
            "Blank response should yield empty values",
            profile.layers.coreIdentity.values.isEmpty()
        )
    }

    // -------------------------------------------------------------------------
    // Test 5 — confidence scores reflect populated field count
    // -------------------------------------------------------------------------

    @Test
    fun `getFullProfile_withFullBlob_confidenceScoresAreNonZero`() = runTest {
        val profileJson = """
            {
              "core_identity": {
                "values": ["honesty"],
                "long_term_goals": ["grow"],
                "fears": ["failure"],
                "worldview": "hopeful",
                "identity_markers": ["leader"]
              },
              "behavioral_patterns": {
                "decision_style": "fast",
                "stress_response": "breathes",
                "work_style": "sprints",
                "risk_tolerance": "high",
                "energy_pattern": "evening"
              },
              "communication_style": {
                "tone_professional": "calm",
                "tone_personal": "funny",
                "vocabulary_markers": ["tbh"],
                "sentence_structure": "long",
                "humor_style": "sarcastic",
                "formality_default": "formal"
              },
              "key_relationships": [],
              "current_context": {
                "active_goals": ["release"],
                "mood_trend": "steady",
                "current_stressors": [],
                "recent_wins": [],
                "pending_decisions": []
              }
            }
        """.trimIndent()

        val mem0Response = """{"results":[{"id":"x","memory":"User personality profile: $profileJson","metadata":{"type":"personality_profile"}}]}"""

        coEvery { mockApiClient.getAllMemories() } returns mem0Response

        val profile = repository.getFullProfile()

        assertTrue(
            "Core identity confidence should be > 0 when all fields populated",
            profile.confidenceScores.coreIdentity > 0f
        )
        assertTrue(
            "Behavioral patterns confidence should be > 0 when all fields populated",
            profile.confidenceScores.behavioralPatterns > 0f
        )
        assertTrue(
            "Communication style confidence should be > 0 when all fields populated",
            profile.confidenceScores.communicationStyle > 0f
        )
    }
}
