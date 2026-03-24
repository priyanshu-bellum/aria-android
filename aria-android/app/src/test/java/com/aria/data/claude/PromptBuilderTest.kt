package com.aria.data.claude

import com.aria.data.local.dao.TodoDao
import com.aria.data.local.entities.Todo
import com.aria.data.memory.Mem0Repository
import com.aria.data.memory.models.BehavioralPatterns
import com.aria.data.memory.models.CommunicationStyle
import com.aria.data.memory.models.CoreIdentity
import com.aria.data.memory.models.CurrentContext
import com.aria.data.memory.models.PersonalityProfile
import com.aria.data.memory.models.ProfileLayers
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PromptBuilderTest {

    private val mockMem0Repository: Mem0Repository = mockk(relaxed = true)
    private val mockTodoDao: TodoDao = mockk(relaxed = true)
    private lateinit var promptBuilder: PromptBuilder

    private val testProfile = PersonalityProfile(
        layers = ProfileLayers(
            coreIdentity = CoreIdentity(
                values = listOf("integrity", "growth"),
                longTermGoals = listOf("build products that matter"),
                fears = listOf("mediocrity"),
                worldview = "Optimistic pragmatist",
                identityMarkers = listOf("builder", "reader")
            ),
            behavioralPatterns = BehavioralPatterns(
                decisionStyle = "analytical",
                stressResponse = "retreats to solo work",
                workStyle = "deep focus blocks",
                riskTolerance = "medium",
                energyPattern = "morning person"
            ),
            communicationStyle = CommunicationStyle(
                toneProfessional = "direct and concise",
                tonePersonal = "warm and witty",
                vocabularyMarkers = listOf("let's ship it", "honestly"),
                sentenceStructure = "short punchy sentences",
                humorStyle = "dry wit",
                formalityDefault = "semi-formal"
            ),
            currentContext = CurrentContext(
                activeGoals = listOf("launch v1"),
                moodTrend = "focused",
                currentStressors = listOf("tight deadline"),
                recentWins = listOf("closed a deal"),
                pendingDecisions = listOf("hire or outsource")
            )
        )
    )

    @Before
    fun setup() {
        promptBuilder = PromptBuilder(mockMem0Repository, mockTodoDao)
        // Default: no pending todos
        coEvery { mockTodoDao.getTopPending(any()) } returns emptyList()
    }

    // -------------------------------------------------------------------------
    // buildAssistantPrompt
    // -------------------------------------------------------------------------

    @Test
    fun `buildAssistantPrompt_containsUserName`() = runTest {
        val prompt = promptBuilder.buildAssistantPrompt(
            profile = testProfile,
            userName = "Alice"
        )
        assertTrue(
            "Prompt should contain the user name 'Alice'",
            prompt.contains("Alice")
        )
    }

    @Test
    fun `buildAssistantPrompt_containsMemories`() = runTest {
        // Seed a todo so we can verify it surfaces in the prompt
        val todo = Todo(
            id = "abc",
            text = "Review the proposal",
            source = "manual",
            completed = false
        )
        coEvery { mockTodoDao.getTopPending(any()) } returns listOf(todo)

        val prompt = promptBuilder.buildAssistantPrompt(
            profile = testProfile,
            userName = "Bob"
        )
        assertTrue(
            "Prompt should contain the todo text",
            prompt.contains("Review the proposal")
        )
    }

    @Test
    fun `buildAssistantPrompt_withEmptyMemories`() = runTest {
        coEvery { mockTodoDao.getTopPending(any()) } returns emptyList()

        val prompt = promptBuilder.buildAssistantPrompt(
            profile = testProfile,
            userName = "Carol"
        )
        assertFalse("Prompt must not be blank with empty todos", prompt.isBlank())
        assertTrue("Prompt should still contain the user name", prompt.contains("Carol"))
        // When there are no todos the builder emits "None"
        assertTrue("Prompt should indicate no open todos", prompt.contains("None"))
    }

    // -------------------------------------------------------------------------
    // buildProxyPrompt
    // -------------------------------------------------------------------------

    @Test
    fun `buildProxyPrompt_containsCommunicationStyle`() {
        val prompt = promptBuilder.buildProxyPrompt(testProfile, "Dave")

        // Vocabulary markers and tone fields should be surfaced
        assertTrue(
            "Proxy prompt should contain professional tone",
            prompt.contains("direct and concise")
        )
        assertTrue(
            "Proxy prompt should contain vocabulary markers",
            prompt.contains("let's ship it")
        )
        assertTrue(
            "Proxy prompt should contain sentence structure",
            prompt.contains("short punchy sentences")
        )
        assertTrue(
            "Proxy prompt should contain humor style",
            prompt.contains("dry wit")
        )
    }

    // -------------------------------------------------------------------------
    // buildSynthesisPrompt
    // -------------------------------------------------------------------------

    @Test
    fun `buildSynthesisPrompt_containsLogEntries`() {
        val logEntries = "09:00 - Had standup\n10:30 - Finished refactor"
        val prompt = promptBuilder.buildSynthesisPrompt(
            logEntries = logEntries,
            profileJson = "{}",
            userName = "Eve"
        )
        assertTrue(
            "Synthesis prompt should contain the log entry text",
            prompt.contains("09:00 - Had standup")
        )
        assertTrue(
            "Synthesis prompt should contain second log entry",
            prompt.contains("10:30 - Finished refactor")
        )
    }

    @Test
    fun `buildSynthesisPrompt_requestsJsonOutput`() {
        val prompt = promptBuilder.buildSynthesisPrompt(
            logEntries = "some log",
            profileJson = "{}",
            userName = "Frank"
        )
        assertTrue(
            "Synthesis prompt should explicitly request JSON output",
            prompt.contains("JSON")
        )
    }

    // -------------------------------------------------------------------------
    // buildOnboardingPrompt
    // -------------------------------------------------------------------------

    @Test
    fun `buildOnboardingPrompt_isNotEmpty`() {
        val prompt = promptBuilder.buildOnboardingPrompt()
        assertFalse("Onboarding prompt must not be blank", prompt.isBlank())
        // Sanity check: it should have meaningful content
        assertTrue(
            "Onboarding prompt should contain question about mornings",
            prompt.contains("morning", ignoreCase = true)
        )
    }

    // -------------------------------------------------------------------------
    // buildTodoExtractionPrompt
    // -------------------------------------------------------------------------

    @Test
    fun `buildTodoExtractionPrompt_containsTranscript`() {
        val transcript = "remind me to call the dentist tomorrow"
        val prompt = promptBuilder.buildTodoExtractionPrompt(transcript)

        assertTrue(
            "Todo extraction prompt should contain the transcript verbatim",
            prompt.contains(transcript)
        )
        assertTrue(
            "Todo extraction prompt should request JSON output",
            prompt.contains("JSON")
        )
    }
}
